# Continuous glass corners

Use the continuous rounded rectangle model from Ahmed Gamil's MIT-licensed
liquid_glass_easy (8e253fee087ba1d3c0928bea4c877427a36e4889). This is a community
approximation, not an exact Apple Dock reconstruction. Extend each corner along
available horizontal/vertical space and reduce smoothing when that space runs out.
Use the same field for coverage, lens displacement, surface normals and optical
edge decorations on all Prismal glass, retaining component dimensions and radii.
Match the glass Dock's CPU shadow and optional foreground border to that contour.
Keep legacy native-blur Dock behavior and configuration unchanged. No new controls.

Alternatives considered: current single cubic per corner does not match the glass
field; ContinuousCornerKit's sampled polygon distance shader is too expensive to
apply per pixel across many glass nodes; Figma paths alone do not provide the
field required by refraction. Prefer the compact glass shader model. Preserve
four-corner radii and square/circle limits. Validate generated shader compilation,
geometry, existing unit tests and APK build; physical appearance requires a device.

## Follow-up: global switch

The user subsequently requested a switch on the Liquid Glass page. This supersedes
"No new controls" above. `liquid_continuous_corners` defaults to false, preserving
main's geometry for missing/old preferences. Enabling uses continuous corners for
all Prismal glass, including matching Dock outline/stroke. Disabling restores the
original rounded-box smoothing and lens normal. Target apps may need restarting.
