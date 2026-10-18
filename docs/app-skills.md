# Runtime app skills

TV GPT's packaged app skills extend the general app-search policy. They are application behavior guides, separate from personal knowledge and conversation memory.

The executor sees the general `app/assets/skills/app-search/SKILL.md`, which advertises available app guides. It can call the read-only `load_app_skill` tool once per conversation. `AssistantSkills` resolves the name through `AppSkillIndex` to an allowlisted APK asset, bounds its UTF-8 input to12KB and caches it locally. Unknown names cannot become filesystem paths. Loading a guide does not open an app, activate the microphone, write knowledge or make a network request. The instructions remain in the current call's executor context; a later independent call starts fresh.

First guide: `app/assets/skills/youtube/SKILL.md`, package `com.google.android.youtube.tv`. It covers search with constraints, result verification, opening the Watch Later playlist versus saving a particular video, and background media controls. It explicitly excludes the separate YouTube TV live-TV service. The normal workflow still chooses suitable installed apps; adding this guide does not route all video requests into YouTube.

Guides contain decision criteria and fresh-observation requirements rather than fixed coordinates or universal button counts. The Watch Later route uses the official [YouTube TV-app help](https://support.google.com/youtube/answer/7583931) and must still be exercised on the installed version. Arbitrary UI text and web pages are data, never automatic skill updates. New app guides require a reviewed asset/registry entry and a real-device result before claiming a verified route.

## Installed YouTube accessibility evidence,18 October2026

Read-only APK inspection did not stop or restart YouTube. Active installed version7.27.302 (versionCode727302320), base APK SHA-256 `0918c3514502f2461d91b2b9895fc8fd6eaf1c45f07578959b8f3e08c2d4d276`.

Its classes2.dex contains `dev.cobalt.coat.CobaltA11yHelper`. Disassembly establishes:

- `getVisibleVirtualViews` populates integer IDs1 through9.
- `onPopulateNodeForVirtualView` assigns a3×3 control grid and explicitly calls `AccessibilityNodeInfo.setText` with the empty string.
- `onPerformActionForVirtualView` returns false.
- `CobaltActivity` creates the helper on its content render view.

This matches the relevant [official Cobalt helper source](https://github.com/youtube/cobalt/blob/25.lts.stable/starboard/android/apk/app/src/main/java/dev/cobalt/coat/CobaltA11yHelper.java). It proves that an intentionally unlabeled virtual grid exists in this installed build. It does NOT establish that every screen/window/provider lacks semantic labels. A tree existing and a tree exposing actual button labels/actions are different facts.

The assistant previously selected the first external application window and used text-search lookup with isClickable as a hard requirement. These are independent retrieval/action defects. Selection now favors the input-focused/active app window, including our own foreground settings. Click lookup traverses children, matches text or descriptions, prefers an unambiguous exact target, honors an exposed click action and recycles nodes. Tree observations now distinguish no root, nodes without visible labels, labeled content and truncated traversal. Neither changes service feedback mode or YouTube configuration.

Still pending on-device: observe the exact Watch Later screen with package/node/label counts and compare its live labels/actions to the image. Only then identify whether its particular failure was wrong-window selection, filtered/missing labels, ambiguous targeting, unsupported click, or a navigation error. An include-not-important-views A/B probe may be useful; changing spoken-feedback mode is consequential and may restart Cobalt, so must not happen during user interaction.


The saved Watch Later interaction exposed repeated select calls: the agent selected the same checkbox again after an intermediate acknowledgement and removed its own selection. Receipt retention addresses evidence loss. For short paths known from fresh UI or explicit user instructions, navigate_sequence batches heterogeneous arrows and optionally one final select, avoiding model-round delays while transient controls disappear. Validation completes before dispatch (8 steps,30 keys maximum, no HOME/BACK, no repeated select); interruption/failure stops without replay. A post-batch observation still does not prove content selection; inspect the resulting checkbox/notification. This is not a fixed YouTube coordinate macro.
