# Third-party attributions

itunda's own hand-drawn glyph system ("itundaface") is original artwork built
from scratch for this project, with one deliberate exception documented here.

## Noto Emoji (Google) — People & Body, Animals & Nature, Food & Drink,
## Travel & Places, Activities, and Objects

The following itundaface glyphs' silhouette/shape path data is derived
directly from Google's real, published **Noto Emoji** project
(<https://github.com/googlefonts/noto-emoji>), not hand-approximated. This
list covers phases 2, 3, 4, 5, 6, and 7 of itundaface's "reach TossFace's
3,600-glyph scale" emoji-input initiative (phase 1, Smileys & Emotion, is
itundaface's own original hand-authored face template, not Noto-derived, and
carries no attribution obligation here):

**People & Body** (outer silhouette only, recolored to itunda's own
skin-tone and indigo brand palette and paired with itundaface's own
accent-stroke signature — Noto's real multi-layer shading was not
reproduced, except for Handshake, whose internal finger-interlock detail is
semantically load-bearing, not decoration, so all of Noto's real shading
paths are kept there, clipped to the outer silhouette):
- Waving Hand (👋), Raised Fist (✊), Victory Hand (✌️), OK Hand (👌),
  Flexed Biceps / Muscle (💪), Folded Hands / Pray (🙏), Clapping Hands (👏,
  silhouette + motion-line placement), Handshake (🤝, full shading kept)

**Animals & Nature, Food & Drink, Travel & Places, Activities, Objects**
(kept in Noto's own real multi-color construction — a monochrome dog, pizza
slice, or soccer ball isn't recognizable — colors are Noto's own real fills,
not itunda's palette): Dog, Cat, Star, Glowing Star, Rainbow, Cherry
Blossom, Bird; Pizza, Hamburger, Coffee, Cake, Donut, Strawberry,
Watermelon, Apple; Car, Airplane, House, Rocket, Bike, Globe; Soccer Ball,
Basketball, Video Game, Palette, Musical Note, Party Popper, Trophy, Direct
Hit; Credit Card, Mobile Phone, Watch, Key, Light Bulb, Headphones,
Paperclip, Pen.

No Noto asset files (fonts, images) are bundled — only path/shape coordinate
data, adapted directly into each platform's own source files.

**Copyright 2013 Google LLC.** Licensed under the SIL Open Font License,
Version 1.1 (<https://scripts.sil.org/OFL>). The full license text is
reproduced below per OFL §2, which requires that any copy — original or
modified — carry this notice.

Locations using this derived data (updated 2026-09-04: extended from People-only
to all six Noto-derived categories, and corrected the published-repo status --
see [[project_itunda_pure_tossface_icons]], it did not actually carry this data
until this same pass):
- `services/micro-frontends/bank-mfe/src/icons/ItundaFace{People,Nature,Food,Travel,Activities,Objects}.tsx` (web)
- `android/core/designsystem/src/main/java/rw/itunda/core/designsystem/itundaface/ItundaFace{People,Nature,Food,Travel,Objects}.kt` (Android), plus `android/features/talk/impl/src/main/java/rw/itunda/feature/talk/impl/ItundaFaceActivities.kt` (Activities lives with Talk's own emoji-picker consumer, not `:core:designsystem`)
- `ios/Core/DesignSystem/Sources/ItundaFace/ItundaFace{People,Nature,Food,Travel,Objects}.swift`, `ios/App/Sources/ItundaFace/ItundaFaceActivities.swift` (iOS)
- `github.com/itunda-rw/itundaface` (the published, standalone itundaface repo —
  see that repo's own `NOTICE_THIRD_PARTY.md`)

---

## SIL Open Font License, Version 1.1

```
Copyright 2013 Google LLC

This Font Software is licensed under the SIL Open Font License, Version 1.1.
This license is copied below, and is also available with a FAQ at:
https://scripts.sil.org/OFL


-----------------------------------------------------------
SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
-----------------------------------------------------------

PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide
development of collaborative font projects, to support the font creation
efforts of academic and linguistic communities, and to provide a free and
open framework in which fonts may be shared and improved in partnership
with others.

The OFL allows the licensed fonts to be used, studied, modified and
redistributed freely as long as they are not sold by themselves. The
fonts, including any derivative works, can be bundled, embedded,
redistributed and/or sold with any software provided that any reserved
names are not used by derivative works. The fonts and derivatives,
however, cannot be released under any other type of license. The
requirement for fonts to remain under this license does not apply
to any document created using the fonts or their derivatives.

PERMISSION & CONDITIONS
Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed, modify,
redistribute, and sell modified and unmodified copies of the Font
Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components,
in Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled,
redistributed and/or sold with any software, provided that each copy
contains the above copyright notice and this license. These can be
included either as stand-alone text files, human-readable headers or
in the appropriate machine-readable metadata fields within text or
binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font
Name(s) unless explicit written permission is granted by the corresponding
Copyright Holder. This restriction only applies to the primary font name as
presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font
Software shall not be used to promote, endorse or advertise any
Modified Version, except to acknowledge the contribution(s) of the
Copyright Holder(s) and the Author(s) or with their explicit written
permission.

5) The Font Software, modified or unmodified, in part or in whole,
must be distributed entirely under this license, and must not be
distributed under any other license. The requirement for fonts to
remain under this license does not apply to any document created
using the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are
not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE
COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL
DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM
OTHER DEALINGS IN THE FONT SOFTWARE.
```
