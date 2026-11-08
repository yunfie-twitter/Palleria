const TRANSPARENT_ALPHA: u8 = 16;
const DOMINANT_BUCKETS: usize = 16 * 16 * 16;

// Callers hand us downscaled color samples (32x32 / 40x40), so the parallel split below must not
// spin up rayon jobs whose per-job 4 KiB histogram costs more than the pixels they scan.
const PARALLEL_MIN_CHUNK_PIXELS: usize = 4096;

#[derive(Debug, uniffi::Record)]
pub struct ImageAnalysis {
    pub average_luminance: f64,
    pub dominant_argb: i32,
    pub sample_count: u32,
}

use rayon::prelude::*;

#[derive(Clone)]
struct AnalysisState {
    luminance_sum: f64,
    sample_count: u32,
    bucket_counts: Vec<u32>,
}

impl Default for AnalysisState {
    fn default() -> Self {
        Self {
            luminance_sum: 0.0,
            sample_count: 0,
            bucket_counts: vec![0; DOMINANT_BUCKETS],
        }
    }
}

impl AnalysisState {
    #[inline]
    fn accumulate(&mut self, pixel: &[u8], lut: &[f64; 256]) {
        let [red, green, blue, alpha] = [pixel[0], pixel[1], pixel[2], pixel[3]];
        if alpha < TRANSPARENT_ALPHA {
            return;
        }

        self.luminance_sum += relative_luminance(red, green, blue, lut);
        self.sample_count += 1;
        self.bucket_counts[bucket_index(red, green, blue)] += 1;
    }
}

#[inline(always)]
fn bucket_index(red: u8, green: u8, blue: u8) -> usize {
    ((red as usize >> 4) << 8) | ((green as usize >> 4) << 4) | (blue as usize >> 4)
}

use std::sync::OnceLock;

static LINEARIZE_LUT: OnceLock<[f64; 256]> = OnceLock::new();

fn get_linearize_lut() -> &'static [f64; 256] {
    LINEARIZE_LUT.get_or_init(|| {
        let mut lut = [0.0; 256];
        for (i, item) in lut.iter_mut().enumerate() {
            let normalized = (i as f64) / 255.0;
            *item = if normalized <= 0.03928 {
                normalized / 12.92
            } else {
                ((normalized + 0.055) / 1.055).powf(2.4)
            };
        }
        lut
    })
}

#[uniffi::export]
pub fn analyze_rgba(pixels: Vec<u8>) -> ImageAnalysis {
    let lut = get_linearize_lut();
    let pixel_count = pixels.len() / 4;
    let result = if pixel_count < PARALLEL_MIN_CHUNK_PIXELS {
        let mut state = AnalysisState::default();
        for pixel in pixels.as_chunks::<4>().0 {
            state.accumulate(pixel, lut);
        }
        state
    } else {
        pixels
            .par_chunks_exact(4)
            .with_min_len(PARALLEL_MIN_CHUNK_PIXELS)
            .fold(AnalysisState::default, |mut state, pixel| {
                state.accumulate(pixel, lut);
                state
            })
            .reduce(AnalysisState::default, |mut a, b| {
                a.luminance_sum += b.luminance_sum;
                a.sample_count += b.sample_count;
                for (left, right) in a.bucket_counts.iter_mut().zip(b.bucket_counts) {
                    *left += right;
                }
                a
            })
    };

    // Ties resolve to the last bucket, matching the previous per-bucket max_by_key scan.
    let winner = result
        .bucket_counts
        .iter()
        .enumerate()
        .max_by_key(|(_, count)| **count)
        .filter(|(_, count)| **count > 0)
        .map(|(index, _)| index);

    let dominant_argb = match winner {
        Some(index) => {
            // Counts alone would not give us the bucket's average colour, so rescan the sample
            // for that one bucket. The samples are tiny; this is cheaper than carrying 3 u64
            // sums per bucket through the histogram.
            let (red, green, blue) = dominant_bucket_sums(&pixels, index);
            let count = u64::from(result.bucket_counts[index]);
            argb(
                (red / count) as u8,
                (green / count) as u8,
                (blue / count) as u8,
            )
        }
        None => argb(0, 0, 0),
    };

    ImageAnalysis {
        average_luminance: if result.sample_count == 0 {
            0.0
        } else {
            result.luminance_sum / f64::from(result.sample_count)
        },
        dominant_argb,
        sample_count: result.sample_count,
    }
}

fn dominant_bucket_sums(pixels: &[u8], index: usize) -> (u64, u64, u64) {
    if pixels.len() / 4 < PARALLEL_MIN_CHUNK_PIXELS {
        let mut totals = (0_u64, 0_u64, 0_u64);
        for pixel in pixels.as_chunks::<4>().0 {
            add_to_bucket(pixel, index, &mut totals);
        }
        totals
    } else {
        pixels
            .par_chunks_exact(4)
            .with_min_len(PARALLEL_MIN_CHUNK_PIXELS)
            .fold(
                || (0_u64, 0_u64, 0_u64),
                |mut totals, pixel| {
                    add_to_bucket(pixel, index, &mut totals);
                    totals
                },
            )
            .reduce(
                || (0_u64, 0_u64, 0_u64),
                |a, b| (a.0 + b.0, a.1 + b.1, a.2 + b.2),
            )
    }
}

#[inline]
fn add_to_bucket(pixel: &[u8], index: usize, totals: &mut (u64, u64, u64)) {
    let [red, green, blue, alpha] = [pixel[0], pixel[1], pixel[2], pixel[3]];
    if alpha < TRANSPARENT_ALPHA || bucket_index(red, green, blue) != index {
        return;
    }
    totals.0 += u64::from(red);
    totals.1 += u64::from(green);
    totals.2 += u64::from(blue);
}

#[inline(always)]
fn relative_luminance(red: u8, green: u8, blue: u8, lut: &[f64; 256]) -> f64 {
    0.2126 * lut[red as usize] + 0.7152 * lut[green as usize] + 0.0722 * lut[blue as usize]
}

fn argb(red: u8, green: u8, blue: u8) -> i32 {
    u32::from_be_bytes([255, red, green, blue]) as i32
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn computes_known_black_and_white_luminance() {
        let black = analyze_rgba(vec![0, 0, 0, 255]);
        let white = analyze_rgba(vec![255, 255, 255, 255]);
        assert_eq!(black.average_luminance, 0.0);
        assert!((white.average_luminance - 1.0).abs() < f64::EPSILON);
    }

    #[test]
    fn ignores_transparent_pixels_and_finds_the_dominant_bucket() {
        let result = analyze_rgba(vec![
            255, 0, 0, 255, 250, 5, 5, 255, 0, 0, 255, 255, 255, 255, 255, 0,
        ]);
        assert_eq!(result.sample_count, 3);
        assert_eq!(result.dominant_argb, argb(252, 2, 2));
    }

    #[test]
    fn handles_empty_and_incomplete_buffers() {
        let result = analyze_rgba(vec![255, 0, 0]);
        assert_eq!(result.sample_count, 0);
        assert_eq!(result.dominant_argb, argb(0, 0, 0));
    }

    #[test]
    fn breaks_dominant_bucket_ties_toward_the_last_bucket() {
        let result = analyze_rgba(vec![0, 0, 0, 255, 255, 255, 255, 255]);
        assert_eq!(result.dominant_argb, argb(255, 255, 255));
    }

    #[test]
    fn analyzes_large_samples_on_the_parallel_path() {
        let mut pixels = Vec::with_capacity(64 * 64 * 4);
        for _ in 0..(64 * 64) {
            pixels.extend_from_slice(&[255, 0, 0, 255]);
        }
        let result = analyze_rgba(pixels);
        assert_eq!(result.sample_count, 64 * 64);
        assert_eq!(result.dominant_argb, argb(255, 0, 0));
        assert!((result.average_luminance - 0.2126).abs() < 1e-9);
    }
}
