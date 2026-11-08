use criterion::{BatchSize, Criterion, black_box, criterion_group, criterion_main};
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

fn benchmark_sample(criterion: &mut Criterion, name: &str, sample: &[u8]) {
    criterion.bench_function(name, |bencher| {
        // `analyze_rgba` takes the buffer by value, so clone it in the untimed setup instead of
        // inside the measured closure.
        bencher.iter_batched(
            || sample.to_vec(),
            |pixels| black_box(analyze_rgba(pixels)),
            BatchSize::SmallInput,
        );
    });
}

fn analyze_image_samples(criterion: &mut Criterion) {
    benchmark_sample(criterion, "analyze_rgba/40x40", &rgba_sample(40, 40));
    benchmark_sample(criterion, "analyze_rgba/32x32", &rgba_sample(32, 32));
    benchmark_sample(criterion, "analyze_rgba/512x512", &rgba_sample(512, 512));
}

criterion_group!(benches, analyze_image_samples);
criterion_main!(benches);
