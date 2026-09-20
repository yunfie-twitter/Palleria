use criterion::{Criterion, black_box, criterion_group, criterion_main};
use palleria_pixiv_api::analyze_rgba;

fn rgba_sample(width: usize, height: usize) -> Vec<u8> {
    (0..width * height)
        .flat_map(|index| {
            let red = (index.wrapping_mul(17) & 0xff) as u8;
            let green = (index.wrapping_mul(29) & 0xff) as u8;
            let blue = (index.wrapping_mul(43) & 0xff) as u8;
            [red, green, blue, 255]
        })
        .collect()
}

fn analyze_image_samples(criterion: &mut Criterion) {
    let sample_40x40 = rgba_sample(40, 40);
    criterion.bench_function("analyze_rgba/40x40", |bencher| {
        bencher.iter(|| analyze_rgba(black_box(sample_40x40.clone())));
    });

    let sample_32x32 = rgba_sample(32, 32);
    criterion.bench_function("analyze_rgba/32x32", |bencher| {
        bencher.iter(|| analyze_rgba(black_box(sample_32x32.clone())));
    });
}

criterion_group!(benches, analyze_image_samples);
criterion_main!(benches);
