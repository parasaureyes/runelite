Scene mirror review companion

This branch adds review-support/ to the source proposal. Submit scene-mirror-api
for review; this branch is a runnable development example and is not for merging.

Requirements: Java 11, network access for Gradle dependencies, supported GPU.
From the repository root (use gradlew.bat on Windows):

  ./gradlew -I review-support/client.init.gradle :client:shadowJar :client:copyReviewInjectedClient
  ./gradlew -p review-support buildReviewClient pluginJar
  ./gradlew -p review-support verifyClasses verifyPicking verifyCamera
  ./gradlew -p review-support runReviewClient

The client opens with a separate ScapeRune Review configuration profile and
loads the example plugin through ExternalPluginManager.loadBuiltin. It uses
the normal RuneLite home, including existing Jagex developer credentials.
For Jagex accounts follow the official setup if needed:
https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts
Do not commit or share credentials. The example does not export credentials.

Build outputs are ignored. No binaries are redistributed. The fixture builder
uses ASM only at build time. The plugin JAR contains only com/scaperune/mirror;
it calls the proposed public SceneMirror service and contains no patch tooling.
The packaged review client contains the companion and a callback adapter.

Native build: injected-client 1.13.2-SNAPSHOT, build ID 37633256718.280
SHA-256: f5364308262c7fc42309174c6a96d6129c7b82146be22c5935123b7ea8700a15
The builder rejects any other artifact or mismatch with the shaded client.
If Maven's snapshot changes, provide this exact artifact as
review-support/build/input/injected-client.jar and use a matching client build.
It must come from RuneLite's dependency distribution, not an untrusted source.

Concrete native sites for this build (not stable names for upstream hooks):
- xk.al(III)V: static entity hover pointer setter; X argument 0, Y argument 1.
- ei.dt(III)V: scene terrain press setter; X argument 1, Y argument 2.
- ry.ag(IIII)V: successful projected X write to bo.ae, before integer encoding;
  invalid branches and projected Y are unchanged.
- client: advertise isSceneMirroringSupported only with the above mappings.
ScenePickingPatcher and SceneMirrorNativeAdapter provide the exact operations.
Source integration semantics are in ../runelite-api/SCENE_MIRRORING.txt.

Manual review: compare mirror on/off; entity hover, left/right click and walking;
inventory/chat/menus/minimap; middle-mouse yaw/pitch; resize and stretched mode;
disable GPU/plugin and return. Keep all in-game input manual.

Scope: GPU scene composition, native scene coordinates, world projections,
outline winding and camera compensation are all needed for one coherent mirror.
Software rendering and 117 HD are outside this change.
