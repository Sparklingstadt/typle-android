# typle-a

Typle の Android タブレット版（Kotlin・Jetpack Compose）です。Web 版・サーバーは [typle-r](https://github.com/Sparklingstadt/typle-r) にあります。構成と確認の手順は README.md を読んでください。

- 変更したら `cd core && ../gradlew test`・`node --test tests/` と、`./gradlew :app:assembleDebug` を実行します。
- `core/src/main/resources` の最初の単語リストは typle-r の `src/plugins/packs` の写しです。直接書き換えず、typle-r から写します（README の「単語リストを Web 版に合わせる」）。
- 文言は `app/src/main/assets/messages/ja.json`・`en.json` の両方に同じキーで入れます。

## 出力の言語

出力は必ず日本語で行うこと。
