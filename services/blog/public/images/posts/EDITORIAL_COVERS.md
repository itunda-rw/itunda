# Itunda Tech editorial covers

Every Itunda Tech article is guaranteed to have a visual cover through `getEditorialImage()` in `src/posts/index.ts`.

## Cover policy

- Prefer a real editorial asset in the article's `image` field.
- Use 16:9 composition for article covers.
- Keep the visual language distinctly Itunda: indigo `#7472F4`, soft lavender surfaces, clean geometric depth, generous negative space.
- Generated fallback covers are deterministic SVG artwork, so discovery cards and article pages never render without an image.
- When a generated raster asset is available, store it under `public/images/posts/<slug>/hero.webp` or `hero.avif` and keep `imageAlt` descriptive.

The blog is built from `services/blog` and deployed as the dedicated `itunda-tech-blog` Cloud Run service on Google Cloud Platform. Its custom hostname is `tech-blog.itunda.im`.
