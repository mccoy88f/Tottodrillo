# Tottodrillo 🎮

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)
![MinSDK](https://img.shields.io/badge/MinSDK-26-orange.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

**Tottodrillo**は、レトロゲームの公開データベースである[CrocDB](https://crocdb.net)からROMを探索、検索、ダウンロードするためのモダンでミニマルなAndroidアプリです。

## 🌍 他の言語 / Other Languages

このREADMEは他の言語でも利用可能です：

- [🇬🇧 English](README.md)
- [🇮🇹 Italiano](README.it.md)
- [🇪🇸 Español](README.es.md)
- [🇩🇪 Deutsch](README.de.md)
- [🇫🇷 Français](README.fr.md)
- [🇨🇳 简体中文](README.zh-CN.md)
- [🇵🇹 Português](README.pt.md)

---

## ✨ 主な機能

### 🔍 探索と検索
- **ホーム画面**：おすすめROM、人気プラットフォーム、お気に入り、最近のROM
- **プラットフォーム探索**：ブランド別に整理（Nintendo、PlayStation、Sega、Xboxなど）、折りたたみ/展開可能なセクション
- **高度な検索**：自動デバウンス（500ms）によるクエリ最適化
- **複数フィルター**：プラットフォームと地域のインタラクティブチップ
- **無限スクロール**：自動遅延読み込み
- **ROM表示**：中央配置で比例したカバーアート

### 📥 ダウンロードとインストール
- **バックグラウンドダウンロード**：WorkManagerによる信頼性の高いダウンロード
- **リアルタイム進捗追跡**：パーセンテージ、ダウンロード済みバイト数、速度
- **インタラクティブ通知**：「ダウンロードをキャンセル」「インストールをキャンセル」アクション
- **カスタムパス**：任意のフォルダ（外部SDカードを含む）にファイルを保存
- **自動/手動インストール**：
  - ZIPアーカイブのサポート（展開）
  - 非アーカイブファイルのサポート（コピー/移動）
  - カスタム宛先のフォルダピッカー
- **ES-DE互換性**：
  - ES-DEフォルダ構造への自動インストール
  - ES-DE ROMsフォルダの選択
  - `mother_code`による自動整理（例：`fds/`、`nes/`など）
- **ファイル管理**：
  - 既存ファイルの上書き（フォルダ内の他のファイルは削除しない）
  - インストール後の元ファイルのオプション削除
  - ダウンロードと展開履歴の管理
- **高度なオプション**：
  - モバイルデータ節約のWiFiのみダウンロード
  - ダウンロード前の利用可能容量確認
  - 設定可能な通知

### 💾 ROM管理
- **お気に入り**：ファイルベースの永続化
- **最近のROM**：最後に開いた25個、ファイルベースの永続化
- **ダウンロード/インストール状態**：各リンクの自動更新
- **状態アイコン**：
  - 進捗インジケーター付きダウンロード中
  - パーセンテージ付きインストール中
  - インストール完了（緑アイコン）
  - インストール失敗（赤アイコン、クリックで再試行）
- **インストールフォルダを開く**：アプリから直接

### 🎨 デザインとUI
- **Material Design 3**：自動ダーク/ライトテーマ
- **ミニマルでモダン**なインターフェース
- **スムーズなアニメーション**：Jetpack Compose
- **カバーアート**：遅延読み込み（Coil）と自動中央配置
- **プラットフォームロゴ**：アセットから読み込まれるSVG、フォールバック付き
- **地域バッジ**：絵文字フラグ
- **ROMカード**：統一された最大幅（180dp）

### ⚙️ 設定
- **ダウンロード設定**：
  - カスタムダウンロードフォルダの選択
  - 利用可能容量の表示
  - WiFiのみダウンロード
  - 通知のオン/オフ
- **インストール設定**：
  - インストール後の元ファイル削除
  - フォルダ選択付きES-DE互換性
- **履歴管理**：
  - ダウンロードと展開履歴のクリア（確認付き）
- **アプリ情報**：
  - アプリバージョン
  - GitHubリンク
  - サポートセクション

## 📱 スクリーンショット

*準備中*

## 🏗️ アーキテクチャ

アプリは**クリーンアーキテクチャ**に従い、レイヤー分離を行います：

```
app/
├── data/
│   ├── mapper/              # API → Domain変換
│   ├── model/               # データモデル（API、Platform）
│   ├── remote/               # Retrofit、APIサービス
│   ├── repository/           # リポジトリ実装
│   ├── receiver/             # 通知用BroadcastReceiver
│   └── worker/               # WorkManagerワーカー（Download、Extraction）
├── domain/
│   ├── manager/              # ビジネスロジックマネージャー（Download、Platform）
│   ├── model/                # ドメインモデル（UI）
│   └── repository/           # リポジトリインターフェース
└── presentation/
    ├── components/            # 再利用可能なUIコンポーネント
    ├── common/                # UI Stateクラス
    ├── detail/                # ROM詳細画面
    ├── downloads/             # ダウンロード画面
    ├── explore/               # プラットフォーム探索画面
    ├── home/                  # ホーム画面
    ├── navigation/            # ナビゲーショングラフ
    ├── platform/              # プラットフォーム別ROM画面
    ├── search/                # 検索画面
    ├── settings/              # 設定画面
    └── theme/                 # テーマシステム
```

## 🛠️ 技術スタック

### コア
- **Kotlin** - 主要言語
- **Jetpack Compose** - モダンUIツールキット
- **Material 3** - デザインシステム

### アーキテクチャ
- **MVVM** - アーキテクチャパターン
- **Hilt** - 依存性注入
- **Coroutines & Flow** - 並行処理と反応性
- **StateFlow** - 反応的な状態管理

### ネットワーク
- **Retrofit** - HTTPクライアント
- **OkHttp** - ネットワーク層
- **Gson** - JSONパース
- **Coil** - SVGサポート付き画像読み込み

### ストレージと永続化
- **DataStore** - 永続的な設定
- **WorkManager** - 信頼性の高いバックグラウンドタスク
- **File I/O** - ダウンロード/インストール追跡用`.status`ファイル管理

### ナビゲーション
- **Navigation Compose** - 画面ルーティング
- **Safe Navigation** - 空白画面を避けるバックスタック管理

### バックグラウンドタスク
- **DownloadWorker** - フォアグラウンドサービス付きバックグラウンドファイルダウンロード
- **ExtractionWorker** - バックグラウンドファイル展開/コピー
- **フォアグラウンド通知** - アクション付きインタラクティブ通知

## 🚀 セットアップ

### 前提条件
- Android Studio Hedgehog（2023.1.1）以降
- JDK 17
- Android SDK API 34
- Gradle 8.2+

### インストール

1. **リポジトリをクローン**
```bash
git clone https://github.com/mccoy88f/Tottodrillo.git
cd Tottodrillo
```

2. **Android Studioで開く**
   - ファイル → 開く → プロジェクトフォルダを選択

3. **Gradleを同期**
   - Android Studioが自動的に依存関係を同期します

4. **ビルドと実行**
   - デバイス/エミュレーターを選択
   - 実行 → 'app'を実行

### 設定

APIキーは不要です。アプリはCrocDBの公開APIを使用します：
- ベースURL: `https://api.crocdb.net/`
- ドキュメント: [CrocDB API Docs](https://github.com/cavv-dev/crocdb-api)

## 📦 ビルド

### デバッグビルド
```bash
./gradlew assembleDebug
```

### リリースビルド
```bash
./gradlew assembleRelease
```

APKは次に生成されます: `app/build/outputs/apk/`

## 🎯 詳細機能

### ダウンロードマネージャー
- 複数の同時ダウンロード
- 各ダウンロードの進捗追跡
- 進行中のダウンロードをキャンセル
- 自動再試行付きエラー処理
- 利用可能容量の確認
- 外部SDカードサポート

### インストール
- ZIPアーカイブの展開
- 非アーカイブファイルのコピー/移動
- インストール中の進捗追跡
- 再試行可能な赤いクリック可能アイコンによるエラー処理
- インストール後の自動UI更新
- インストールフォルダを開く

### ES-DE互換性
- 互換性の有効/無効
- ES-DE ROMsフォルダの選択
- 正しい構造への自動インストール
- 自動マッピング `mother_code` → フォルダ

### 履歴管理
- ダウンロード/インストール追跡用`.status`ファイル
- 同じファイルの複数ダウンロードをサポートするマルチライン形式
- ユーザー確認付き履歴クリア

## 🎯 ロードマップ / To Do

将来のバージョンで計画されている機能：

- [ ] **マルチソース構造の実装**
  - CrocDB以外の複数のROMソースのサポート
  - 設定でのソース設定と選択
  - 異なるソースからの結果の統一

- [ ] **ScreenScraper.frサポート**
  - ScreenScraper APIとの統合によるROMデータの充実
  - ユーザーのプライベートアカウントによる名前、説明、画像の改善
  - 設定でのScreenScraper認証情報の設定
  - アカウントが設定されていない場合の自動フォールバック

- [ ] **カスタムROMリストと一括ダウンロード**
  - カスタムROMリストの作成
  - 複数リストの保存と管理
  - リスト内の全ROMの一括ダウンロード
  - 複数ダウンロードの優先順位とキューの管理

## 🌐 ローカライゼーション

アプリは現在8言語をサポートしています：
- 🇮🇹 イタリア語（デフォルト）
- 🇬🇧 英語
- 🇪🇸 スペイン語
- 🇩🇪 ドイツ語
- 🇯🇵 日本語
- 🇫🇷 フランス語
- 🇨🇳 簡体字中国語
- 🇵🇹 ポルトガル語

アプリは自動的にデバイスの言語を使用します。言語がサポートされていない場合、デフォルトでイタリア語を使用します。

## 🤝 貢献

貢献を歓迎します！以下の手順に従ってください：

1. プロジェクトをフォーク
2. 機能用のブランチを作成（`git checkout -b feature/AmazingFeature`）
3. 変更をコミット（`git commit -m 'Add some AmazingFeature'`）
4. ブランチにプッシュ（`git push origin feature/AmazingFeature`）
5. プルリクエストを開く

### ガイドライン
- Kotlinの規則に従う
- UIにはJetpack Composeを使用
- 可能な限りテストを書く
- パブリックAPIを文書化
- コードをクリーンで読みやすく保つ

## 📄 ライセンス

このプロジェクトはMITライセンスの下でリリースされています。詳細については[LICENSE](LICENSE)ファイルを参照してください。

## 🙏 謝辞

### APIとデータベース
- 公開APIとROMデータベースを提供する[CrocDB](https://crocdb.net)
- ROMデータベースとAPIを提供する[cavv-dev](https://github.com/cavv-dev)

### プラットフォームロゴ
プラットフォームSVGロゴは以下によって提供されています：
- [alekfull-nx-es-de](https://github.com/anthonycaccese/alekfull-nx-es-de) - ES-DEロゴリポジトリ

### コミュニティ
- サポートとフィードバックを提供するレトロゲームコミュニティ
- すべての貢献者とアプリテスター

## ⚠️ 免責事項

**重要**：このアプリは教育および研究目的で作成されています。

- ROMの使用には元のゲームの**合法的所有**が必要です
- 常にあなたの国の**著作権法**を尊重してください
- アプリはROMを提供するのではなく、公開データベースへのアクセスを促進するだけです
- 作者はアプリケーションの誤用について責任を負いません

## 📞 連絡先

**作者**: mccoy88f

**リポジトリ**: [https://github.com/mccoy88f/Tottodrillo](https://github.com/mccoy88f/Tottodrillo)

**Issues**: バグを見つけたり、提案がある場合は、[Issue](https://github.com/mccoy88f/Tottodrillo/issues)を開いてください

## ☕ サポート

このプロジェクトが気に入り、サポートしたい場合は、コーヒーをご購入いただけます！🍺

あなたのサポートは、開発を継続し、アプリを改善するのに役立ちます。

<a href="https://www.buymeacoffee.com/mccoy88f">BUY ME A COFFEE!</a>

[PayPalでコーヒーを購入することもできます 🍻](https://paypal.me/mccoy88f?country.x=IT&locale.x=it_IT)

---

**Made with ❤️ for the retro gaming community**

