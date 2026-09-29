# Itunda Tech media

Store article media in this directory using the article slug as the folder name.

Recommended structure:

- `images/posts/<slug>/hero.webp` — 16:9 article cover
- `images/posts/<slug>/01.webp` — inline image
- `images/posts/<slug>/02.webp` — inline image

Use WebP or AVIF for photographic/illustrative media and descriptive `imageAlt` metadata in the post model. Reference inline images from Markdown with `/images/posts/<slug>/01.webp`.

Keep source artwork outside the repository when appropriate; only production-ready web assets belong here.
