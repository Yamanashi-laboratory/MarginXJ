# MarginXJ

[MarginX](https://github.com/Yamanashi-laboratory/MarginX)（C++）を、レイヤードアーキテクチャ + 依存性逆転（DIP）で Java に移植するプロジェクトです。

## レイヤ構成

依存方向は `presentation → application → domain ← infrastructure` の一方向のみ。
`domain/port` にインタフェースを置き、`infrastructure` がそれを実装します。

```
com.ynu.marginx
├─ presentation/          CLI・対話メニュー・進捗バー・整形表示
│   ├─ MarginXCommand         picocli エントリポイント（-j, -d, -m）
│   ├─ InteractiveMenu / OperationMode
│   └─ view/                  ProgressBarView, MarginChartView, DetailView
├─ application/           ユースケース（手順の組み立てのみ。計算はしない）
│   ├─ CalculateMarginUseCase   ExecutorService で素子ごとに並列探索
│   ├─ JudgeOperationUseCase
│   └─ ProgressListener
├─ domain/                外部技術を一切知らない層
│   ├─ model/circuit/       ElementType, CircuitElement, ShuntSpec, ParameterRange, Netlist
│   ├─ model/margin/        Margin, ElementMargin, MarginTable
│   ├─ model/judge/         JudgementRule, JudgementSpec, SimulationResult, JudgementOutcome
│   ├─ port/                CircuitSimulator, NetlistRepository,
│   │                       JudgementSpecRepository, MarginResultRepository
│   └─ service/             MarginSearcher (IF) → Exhaustive / BinarySearch
│                           OperationJudge, OperationEvaluator, CriticalElementFinder
├─ infrastructure/        port の実装（技術詳細の置き場）
│   ├─ netlist/             NetlistParser, NetlistRenderer, FileNetlistRepository
│   ├─ judgement/           JudgementSpecParser, FileJudgementSpecRepository
│   ├─ simulator/           JosimSimulator, ProcessExecutor
│   ├─ result/              JosimCsvReader, FileMarginResultRepository
│   └─ config/              SimulatorProperties
└─ shared/exception/      MarginXException とそのサブクラス
```

レイヤ間の依存規則は ArchUnit（`LayerDependencyTest`）でテストとして機械的に強制しています。
`domain` が `java.nio.file` / `java.io` / picocli / infrastructure を参照した時点でビルドが落ちます。

## 元コードからの対応

| C++ | Java |
|---|---|
| `main.cpp`, `menu.cpp`, `display_*.cpp` | `presentation/` |
| `fig_out.cpp`, `detail_out.cpp`, `Margin.cpp` のインジケータ | `presentation/view/` |
| `Margin.cpp` の fork + 共有メモリ | `CalculateMarginUseCase`（`ExecutorService`） |
| `margin_ele.cpp` | `ExhaustiveMarginSearcher` |
| `margin_ele_low.cpp` | `BinarySearchMarginSearcher` |
| `judge_operation.cpp` | `OperationJudge` |
| `find_critical*.cpp` | `CriticalElementFinder` |
| `circuit_file.cpp`, `find_range.cpp`, `judge_element.cpp`, `sub_unit.cpp` | `NetlistParser`, `UnitPrefix` |
| `make_cir.cpp` の `switch(ide_num)` | `ElementType` の各定数の `render()` |
| `judgement_file.cpp` | `JudgementSpecParser` |
| `readJOSIMData.cpp` | `JosimCsvReader` |
| `file_out.cpp` | `FileMarginResultRepository` |
| `#define JOSIM_COMMAND` | `application.properties` / `-Dmarginx.josim.command=` |
| `function.hpp` の構造体 | `domain/model/` の record |

### `_jsim` 二重化の解消

`Margin.cpp` / `Margin_jsim.cpp` のような対のファイルは、`CircuitSimulator` ポート 1 本に集約しました。
JSIM 用アダプタを追加する際は `infrastructure/simulator/JsimSimulator` を足すだけで、domain 側は変更不要です。

### 中間ファイルの分離方法（移植上の要注意点）

C++ 版は中間ファイルを `MARGIN<pid>.cir` と PID で分離していましたが、Java のスレッドは PID を共有します。
`JosimSimulator` は実行ごとに `Files.createTempDirectory()` で作業ディレクトリを切り、
その中で固定名（`MARGIN.cir` / `CIRCUIT.CSV`）を使って衝突を防いでいます。

## 移植状況

| ステップ | 状態 |
|---|---|
| 1. ネットリスト・判定ファイルのパーサ | 完了 |
| 2. JoSIM + 全探索マージン計算の縦串 | 完了 |
| 3. 二分探索マージン計算 | 完了 |
| 4. 同期探索（`calc_margin_syn`） | 未着手 |
| 5. JSIM アダプタ | 未着手 |
| 6. 最適化アルゴリズム群（CMM / CGM / 逐次 CGM） | 未着手 |
| 7. matplotlib グラフ出力（`margin_py.cpp`） | 未着手 |

対話メニューには実装済みのモードのみを出しています。

## 移植にあたり修正した元コードの不整合

golden ファイルと数値比較する際は、以下の 3 点で C++ 版と結果が変わり得ます。

1. **コンデンサの単位解析** — `sub_unit()` が基本単位の `F` をフェムト接頭辞 `f` として拾うため、
   C++ 版は `0.07pF` の単位を `fF` と誤認します。Java 版は基本単位を除いてから接頭辞を判定します。
2. **コンデンサの素子種別** — C++ 版は `case 4`（C）で `ide_num` に 5（R）を格納していました。
3. **電圧源の値** — C++ 版 `case 6` は消費済みの `stringstream` を再利用しており、PWL の途中の値を拾います。
   Java 版は電流源（`case 7`）と同じく、PWL 末尾の振幅を読みます。

一方、**以下は元の挙動を保存**しています（結果が大きく変わるため）。

- **小文字始まりの素子だけがマージン対象** — `L01` のような大文字始まりの行は対象外。これは元コードの
  `judge_element()` が小文字しか判定しない仕様で、事実上「対象マーカー」として機能しています。
- **シャント抵抗は素子の初期値から計算** — 面積を掃引中でもシャント抵抗は初期面積基準のまま
  （`make_cir.cpp` と同じ。`CircuitElement#renderShuntLine` にコメントあり）。

## 配布と実行（利用者向け）

> [!IMPORTANT]
> **動作には JoSIM のインストールが必須です。** MarginXJ は回路シミュレータそのものではなく、
> JoSIM を外部プロセスとして呼び出してマージンを計算するツールです。
> **JoSIM は MarginXJ に同梱されていません。**
> [JoSIM の Releases](https://github.com/JoeyDelp/JoSIM/releases) から入手して
> インストールしてください。同梱しない理由は
> [ADR 0001](docs/adr/0001-distribution-strategy.md) に記録しています。

利用者に JDK を入れさせないため、Java ランタイムを同梱した OS ネイティブのインストーラを
配布します。`v*` タグを push すると `.github/workflows/release.yml` が OS ごとにビルドし、
Release に並べます。

| 配布物 | 対象 | 導入方法 |
|---|---|---|
| `MarginXJ-<version>.msi` | Windows x86_64 | ダブルクリックしてインストール |
| `marginxj_<version>_amd64.deb` | Linux x86_64（Debian / Ubuntu 系） | `sudo apt install ./marginxj_<version>_amd64.deb` |
| `marginxj-<os>-portable.zip` | Windows / Linux x86_64（インストールせずに使う場合） | 展開して中の起動ファイルを実行 |

いずれも Java ランタイムを含むため、**JDK の導入は不要**です。
portable zip は展開するだけで動きます（Windows は `MarginXJ.exe`、Linux は `bin/MarginXJ`）。

macOS 向けの配布物と、x86_64 以外（Linux arm64 など）向けの配布物は現時点ではありません。
これらの環境では「ビルドと実行（開発者向け）」の手順でソースからビルドしてください。

### JoSIM の場所を教える

JoSIM が PATH にあれば設定は不要です。PATH に無い場合、または特定のビルドを
使いたい場合は、環境変数で場所を指定します。

Linux:

```bash
MARGINX_JOSIM_COMMAND=/usr/local/bin/josim MarginXJ test_JTL -m 2
```

Windows (PowerShell):

```
$env:MARGINX_JOSIM_COMMAND = "C:\tools\josim.exe"
```

インストーラで導入した MarginXJ では、この環境変数が唯一の指定方法です。
`-Dmarginx.josim.command=...` も同じ設定を指しますが、こちらは `java -jar` や
`./gradlew run` で JVM を直接起動する場合にのみ有効です（インストール版の起動ファイルは
コマンドライン引数をアプリケーションへ渡すため、`-D` は JVM に届きません）。

### JSIM について

**JSIM は JoSIM が見つからない場合の代替であり、必須ではありません。** JoSIM を検出できた
場合は常に JoSIM を使います。JSIM も MarginXJ には同梱されないため、使う場合は利用者側で
用意し、同じ方法で場所を指定します。

```bash
MARGINX_JSIM_COMMAND=/usr/local/bin/jsim MarginXJ test_JTL -m 2
```

ただし **JSIM アダプタ自体は未実装です**（移植ステップ 5）。現時点で実際に呼び出せるのは
JoSIM のみで、フォールバックは設計上の位置づけに留まります。

## ビルドと実行（開発者向け）

JDK 21 以上が必要です（バイトコードは 21 固定）。

```bash
./gradlew build
```

`./gradlew build` は通常の JAR に加えて、依存を同梱した `build/libs/marginxj-<version>-all.jar`
（`java -jar` で直接動く実行可能 JAR）も生成します。

### インストーラのビルド

インストーラは JDK 標準の jpackage で作ります。JDK 21 以上で Gradle を動かしていれば
追加のツールチェーンは要りません。形式は Windows なら `.msi`、Linux なら `.deb` が既定で、
出力先は `build/jpackage/<形式>/` です。

```bash
./gradlew jpackage
```

portable zip の中身になる展開済みアプリは、形式を指定して生成します。

```bash
./gradlew jpackage -Pjpackage.type=app-image
```

**Windows で `.msi` を作るには WiX Toolset v3 が必要です**（未導入だと jpackage が
WiX を見つけられずに失敗します）。`app-image` の生成には不要です。Linux で `.deb` を
作るには `dpkg-deb` と `fakeroot` が必要です。

```bash
./gradlew run --args="test_JTL -d -m 2"
```

JoSIM のコマンド名は設定で差し替えられます。

```bash
./gradlew test -Dmarginx.josim.command=/usr/local/bin/josim
```

### 現在の検証状況

配布方式を GraalVM Native Image から JavaFX + jpackage に切り替えた（[ADR 0001](docs/adr/0001-distribution-strategy.md)）
直後のため、ビルド系で未検証の点がまとまって残っています。

検証済み: 切り替え前の構成での `./gradlew clean build` は BUILD SUCCESSFUL、テスト 33 件すべて通過。

未検証:

- **JavaFX プラグイン導入後の `./gradlew build`。** 作業端末に JDK 21 が無く（JDK 11 のみ）、
  Gradle 9.1 自体が起動しないため実行できていません。とくに
  `org.openjfx.javafxplugin` 0.1.0 が Gradle 9 で削除された Convention API に依存していないかは
  実行して確かめる必要があります。
- **jpackage によるインストーラ生成。** `jpackage` タスクは雛形で、一度も成果物を出していません。
  Windows の WiX Toolset、Linux の `dpkg-deb` / `fakeroot` という前提も未確認です。
- **GUI 実装後の起動可否。** 現在は fat jar をそのまま jpackage に渡しています。JavaFX の
  ネイティブライブラリが fat jar 経由で読めるか、`--module-path` や jlink ランタイムが要るかは
  GUI が入ってから確認します。
- **リリース CI。** `.github/workflows/release.yml` は jpackage 前提に書き換えましたが未実行です。

### トラブルシューティング: Windows で Gradle が起動しない場合

`gradlew` が次のエラーで起動しないことがあります。

```
java.io.IOException: Unable to establish loopback connection
Caused by: java.net.SocketException: Invalid argument: connect
```

これは Gradle ではなく JDK 由来です。JDK 25 の `Selector` は起床用パイプに Unix ドメイン
ソケットを使うため（`Pipe.open()` は TCP なので影響を受けません）、AF_UNIX の `connect()` が
遮断されている環境では `Selector.open()` 自体が失敗します。開発端末によっては、
セキュリティ製品などの影響で **`%APPDATA%` と `%LOCALAPPDATA%` 配下でのみ** `connect()` が
失敗する（`bind()` は成功する）ことがあり、JDK の既定の一時ディレクトリが
`%LOCALAPPDATA%\Temp` であるため必ず踏みます。

回避するには、Unix ソケット用の一時ディレクトリをその外に向けるユーザー環境変数を設定します。

```
JAVA_TOOL_OPTIONS = -Djdk.net.unixdomain.tmpdir=%USERPROFILE%/.gradle/uds
```

（実際には環境変数の値は展開されないため、`C:/Users/<user>/.gradle/uds` のように絶対パスで
書き、そのディレクトリを作成しておいてください。）

`org.gradle.jvmargs` では解決しません。Gradle はデーモン起動時に任意の `-D` を取り除くためです
（残るのは `file.encoding` や `user.*` などの固定分のみ）。`JAVA_TOOL_OPTIONS` ならランチャ・
デーモン・テストワーカーすべてに効きます。

- Java 実行のたびに stderr へ `Picked up JAVA_TOOL_OPTIONS: ...` が出ます。これを避けたい場合は、
  代わりに各 JDK の `conf/net.properties` に `jdk.net.unixdomain.tmpdir=...` を 1 行足す方法もあります
  （出力は静かになりますが、JDK を入れ替えるたびに再設定が必要です）。
- 症状が出る端末では Docker や IDE など他の Java ツールでも同種の問題が起き得ます。

## テスト

- `LayerDependencyTest` — レイヤ依存と循環参照の禁止（ArchUnit）
- `NetlistParserTest` / `NetlistRendererTest` — `test_circuits/test_JTL.cir` を固定資産にした特性化テスト
- `JudgementSpecParserTest` — 判定ファイルの解釈
- `OperationJudgeTest` — 位相しきい値の判定と `anti` 反転
- `MarginSearcherTest` — 既知の動作窓を返すダミーシミュレータに対し、両探索法が窓を当てられるか
- `JosimSimulatorProcessTest` — **外部プロセスを実際に起動**する統合テスト（下記）
- `RealJosimIT` — 実 JoSIM に対するテスト。既定ではスキップ

### 実シミュレータでのテスト

`JosimSimulatorProcessTest` は、JoSIM と同じ規約で振る舞うスタブ（作業ディレクトリの
ネットリストを読み、`.FILE` の指定先へ CSV を書く）を **本物の外部プロセスとして起動**します。
JoSIM 自体の物理計算以外は実物と同じ経路を通るため、次を実測で検証しています。

- レンダリング済みネットリストがプロセスに届くこと、掃引値が反映されること
- `.FILE` の書き換えと CSV の読み戻し（時間列の 10^12 倍を含む）
- 中間ファイルの分離（32 並列で互いの値を踏まないこと）— PID 依存を排した箇所の回帰テスト
- 実行後に作業ディレクトリが残らないこと、実行ファイルが無い場合にハングせず例外になること

実物の JoSIM を使う場合はコマンドを指定します。指定が無ければ `RealJosimIT` はスキップされます。

```bash
./gradlew test -Dmarginx.it.josim=josim
```

未実施の確認事項が 1 点あります。この端末に JoSIM が無いため `RealJosimIT` は一度も実行できて
おらず、**実 JoSIM が `.FILE` ディレクティブで出力先を決めるか**は未検証です（C++ 版がその前提で
動いていたためそのまま移植しています）。もし出力が得られない場合は `JosimSimulator` を
`-o <出力ファイル>` を渡す形に変える必要があります。

JSIM については、アダプタ自体が未実装（移植ステップ 5）のため実行するものがありません。
