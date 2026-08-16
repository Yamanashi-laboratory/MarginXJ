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
│   ├─ OptimizeCircuitUseCase   最適化結果の回路と margins を保存
│   ├─ ParallelOperationSampler モンテカルロ 1 サイクル分を並列評価
│   ├─ JudgeOperationUseCase
│   └─ ProgressListener
├─ domain/                外部技術を一切知らない層
│   ├─ model/circuit/       ElementType, CircuitElement, ShuntSpec, ParameterRange, Netlist
│   ├─ model/margin/        Margin, ElementMargin, MarginTable
│   ├─ model/judge/         JudgementRule, JudgementSpec, SimulationResult, JudgementOutcome
│   ├─ model/optimize/      ScoreWeights, OptimizationOutcome
│   ├─ port/                CircuitSimulator, NetlistRepository,
│   │                       JudgementSpecRepository, MarginResultRepository
│   └─ service/             MarginSearcher (IF) → Exhaustive / BinarySearch
│                           OperationJudge, OperationEvaluator, CriticalElementFinder
│                           CriticalMarginMethod, CenterOfGravityOptimizer
│                           CriticalMarginCalculator, ScoreCalculator
│                           MarginTableCalculator, OperationSampler, RandomSource
├─ infrastructure/        port の実装（技術詳細の置き場）
│   ├─ netlist/             NetlistParser, NetlistRenderer, FileNetlistRepository
│   │                       JsimPrintDirectiveConverter
│   ├─ judgement/           JudgementSpecParser, FileJudgementSpecRepository
│   ├─ simulator/           ExternalProcessSimulator → JosimSimulator / JsimSimulator
│   │                       SimulatorRegistry, ProcessExecutor
│   ├─ result/              JosimCsvReader, JsimCsvReader, FileMarginResultRepository
│   └─ config/              SimulatorProperties, SimulatorKind,
│                           SimulatorLocation, UserSimulatorSettings
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
| `readJSIMdata.cpp` | `JsimCsvReader` |
| `convert_jsim.cpp` | `JsimPrintDirectiveConverter` |
| `margin_ele_syn.cpp`, `synchro.cpp` | `ExhaustiveMarginSearcher`（同期モード） |
| `critical_margin_method.cpp` | `CriticalMarginMethod` |
| `optimize_yield_up.cpp`, `optimize_seq.cpp`, `opt_ele_yield.cpp` | `CenterOfGravityOptimizer` |
| `calc_score.cpp`, `select_score.cpp` | `ScoreCalculator`, `ScoreWeights`, `ScoreChoice` |
| `calc_critical*.cpp` | `CriticalMarginCalculator` |
| `make_cir_last.cpp` | `NetlistRepository#save` |
| `file_out.cpp` | `FileMarginResultRepository` |
| `#define JOSIM_COMMAND` | `SimulatorLocation`（保存設定 → 環境変数 → `-D` → PATH → 既定の場所） |
| `menu 8`（JSIM サブメニュー） | `--simulator` と `SimulatorRegistry`（モードから独立） |
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
| 4. 同期探索（`calc_margin_syn`） | 完了 |
| 5. JSIM アダプタ | 完了 |
| 6. 最適化アルゴリズム群（CMM / CGM / 逐次 CGM） | 完了 |
| 7. グラフ出力（`margin_py.cpp` の matplotlib を JavaFX GUI に置き換え） | 未着手 |

対話メニューには実装済みのモードのみを出しています。

## 移植にあたり修正した元コードの不整合

golden ファイルと数値比較する際は、以下の 3 点で C++ 版と結果が変わり得ます。

1. **コンデンサの単位解析** — `sub_unit()` が基本単位の `F` をフェムト接頭辞 `f` として拾うため、
   C++ 版は `0.07pF` の単位を `fF` と誤認します。Java 版は基本単位を除いてから接頭辞を判定します。
2. **コンデンサの素子種別** — C++ 版は `case 4`（C）で `ide_num` に 5（R）を格納していました。
3. **電圧源の値** — C++ 版 `case 6` は消費済みの `stringstream` を再利用しており、PWL の途中の値を拾います。
   Java 版は電流源（`case 7`）と同じく、PWL 末尾の振幅を読みます。
4. **CGM の最良回路の初期値** — C++ 版は `best_value` を全 0・スコア 0 で開始するため、
   一度もスコアを更新できなかった run は**全パラメータが 0 の回路**を返します。Java 版は入力回路を
   初期の最良として扱うので、最悪でも入力がそのまま返ります。

一方、**以下は元の挙動を保存**しています（結果が大きく変わるため）。

- **小文字始まりの素子だけがマージン対象** — `L01` のような大文字始まりの行は対象外。これは元コードの
  `judge_element()` が小文字しか判定しない仕様で、事実上「対象マーカー」として機能しています。
- **シャント抵抗は素子の初期値から計算** — 面積を掃引中でもシャント抵抗は初期面積基準のまま
  （`make_cir.cpp` と同じ。`CircuitElement#renderShuntLine` にコメントあり）。
- **CGM の `lambda` は整数除算** — `optimize_yield_up.cpp` の
  `lambda = (MULTI_NUM - success) / MULTI_NUM` は int 同士の除算で、success が 1 以上なら必ず 0 です。
  つまり補正項は常に消え、残るのは「正常動作した試行の重心」そのもの
  （＝手法名どおりの挙動）。実数除算に"修正"すると別のアルゴリズムになるため保存しています。
- **同期探索のメニュー表記** — 元コードのメニューは 4 番を "binary search with synchro" と表示しますが、
  `main.cpp` は全探索ベースの `Margin_syn` を呼びます。移植はコード側に従っています。

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

### シミュレータの場所

実行ファイルは次の順で探します。上にあるものが優先されます。

1. **設定に保存したパス** — 下記の `--set-josim-path` で永続化した値
2. **環境変数** `MARGINX_JOSIM_COMMAND` / `MARGINX_JSIM_COMMAND`
3. **システムプロパティ** `-Dmarginx.josim.command=` / `-Dmarginx.jsim.command=`
4. **PATH** 上の `josim` / `jsim`（Windows では `PATHEXT` の拡張子も探索）
5. **OS ごとの一般的なインストール先**（`C:\Program Files\JoSIM`、`/usr/local/bin`、
   ビルドツリーの `~/JoSIM/build/Release` など）

PATH にあれば設定は不要です。1〜3 は「どの実行ファイルを使うか」の明示指定なので、
**指定したのに見つからない場合は、黙って他の候補にフォールバックせずエラーになります。**

保存する場合（一度実行すれば以降も有効です）。保存先は OS 標準の設定ディレクトリで、
Windows は `%APPDATA%\MarginXJ`、macOS は `~/Library/Application Support/MarginXJ`、
Linux は `$XDG_CONFIG_HOME`（既定 `~/.config/marginxj`）です。

```bash
MarginXJ --set-josim-path /usr/local/bin/josim
```

環境変数で一時的に指定する場合。

```bash
MARGINX_JOSIM_COMMAND=/usr/local/bin/josim MarginXJ test_JTL -m 2
```

Windows (PowerShell):

```
$env:MARGINX_JOSIM_COMMAND = "C:\tools\josim.exe"
```

`-Dmarginx.josim.command=...` は `java -jar` や `./gradlew run` で JVM を直接起動する場合にのみ
有効です。インストーラ版の起動ファイルはコマンドライン引数をアプリケーションへ渡すため、
`-D` は JVM に届きません。インストール版では 1 か 2 を使ってください。

### JSIM について

**JSIM は JoSIM が見つからない場合の代替であり、必須ではありません。** JoSIM を検出できた
場合は常に JoSIM を使います。JSIM も MarginXJ には同梱されないため、使う場合は利用者側で
用意し、同じ方法で場所を指定します。

```bash
MARGINX_JSIM_COMMAND=/usr/local/bin/jsim MarginXJ test_JTL -m 2
```

両者は数値計算エンジンが異なり、結果が一致する保証はありません。そのため
**フォールバックは決して黙って起きません。**

- JSIM に切り替わる場合、計算を始める前に警告を表示します
  （「JoSIM が見つからないため JSIM を使用します。結果は JoSIM と一致しない可能性があります」）。
- 結果ファイル `result_*.csv` / `result_*.txt` の先頭に、使用したシミュレータ名と解決された
  実行ファイルのパスを `#` 始まりのコメント行として記録します。後から結果ファイルだけを見ても
  出所が分かります。データ行の形式は従来どおりです。

  ```
  # simulator: JSIM
  # executable: C:\Program Files (x86)\jsim\jsim.exe
  J01,-96.5820,99.9023
  ```

使うシミュレータは実行モードとは独立した最上位の設定です（C++ 版のような「JSIM 専用サブメニュー」は
設けていません）。`--simulator josim|jsim|auto` で明示でき、既定は `auto` です。明示した側が
見つからない場合は、もう一方に切り替えずエラーになります。

```bash
MarginXJ test_JTL -m 3 --simulator jsim
```

JSIM 特有の癖は 2 点あり、いずれも実機の JSIM で確認したうえで吸収しています。
デッキ全体を大文字化するため出力ファイル名も大文字になること（`.FILE CIRCUIT.CSV` と大文字で
渡すことで一致させています）と、CSV にヘッダ行が無いことです。また JSIM は回路が発散した際に
MSVC 形式の `-1.#IO` などを書きながら終了コード 0 を返すため、これらは非有限値として読み、
その試行は「動作せず」と判定します。

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

検証済み:

- `./gradlew clean build` は **BUILD SUCCESSFUL**、テスト **90 件すべて通過**（JDK 26、
  実 JoSIM と実 JSIM を指定した状態で計測。指定が無い場合は IT 4 件がスキップされます）。
  `org.openjfx.javafxplugin` 0.1.0 は Gradle 9 でも問題なく動作します。
- **実 JoSIM 2.6.8 に対する `RealJosimIT` が通過。** かねてより未検証だった
  「実 JoSIM が `.FILE` で出力先を決めるか」は**決める**ことを実測で確認しました。`-o` への変更は不要です。
- **実 JSIM に対する `RealJsimIT` が通過。**
- CMM（モード 5）は実 JoSIM で通しで動作し、`<回路名>_out.cir` を出力することを確認済み。

未検証:

- **jpackage によるインストーラ生成。** `jpackage` タスクは雛形で、一度も成果物を出していません。
  Windows の WiX Toolset、Linux の `dpkg-deb` / `fakeroot` という前提も未確認です。
- **GUI 実装後の起動可否。** 現在は fat jar をそのまま jpackage に渡しています。JavaFX の
  ネイティブライブラリが fat jar 経由で読めるか、`--module-path` や jlink ランタイムが要るかは
  GUI が入ってから確認します。
- **リリース CI。** `.github/workflows/release.yml` は jpackage 前提に書き換えましたが未実行です。
- **最適化モード（5/6/7）の実回路での収束。** 単体テストはスタブシミュレータに対する振る舞いで
  固定しており、実 JoSIM で CGM を既定設定（500 サイクル × 100 試行）まで回した実績はありません。
  乱数に依存するため C++ 版と数値が一致することは原理的にありません（元コード自身も
  `random_device` で毎回異なる結果を出します）。

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
- `CriticalMarginMethodTest` / `CenterOfGravityOptimizerTest` — 最適化アルゴリズム。乱数は
  固定シードで与え、「窓の中央へ寄る」「入力より悪い回路を返さない」「`*FIX` を動かさない」など
  シードに依存しない性質を検証
- `JsimPrintDirectiveConverterTest` / `JsimCsvReaderTest` — JSIM 向けの変換と、ヘッダ無し CSV・
  発散値（`-1.#IO`）の解釈
- `JosimSimulatorProcessTest` / `JsimSimulatorProcessTest` — **外部プロセスを実際に起動**する統合テスト（下記）
- `SimulatorLocationTest` — 5 段階の解決順（保存設定 → 環境変数 → システムプロパティ → PATH →
  一般的インストール先）と、明示指定が解決できない場合にフォールバックせず失敗すること
- `SimulatorRegistryTest` — JoSIM のみ / JSIM のみ / 両方 / どちらも無し / 明示指定 の選択
- `FileMarginResultRepositoryTest` — 結果ファイル先頭の出所記録と、データ行の形式が変わらないこと
- `RealJosimIT` / `RealJsimIT` — 実シミュレータに対するテスト。既定ではスキップ

### 実シミュレータでのテスト

`JosimSimulatorProcessTest` は、JoSIM と同じ規約で振る舞うスタブ（作業ディレクトリの
ネットリストを読み、`.FILE` の指定先へ CSV を書く）を **本物の外部プロセスとして起動**します。
JoSIM 自体の物理計算以外は実物と同じ経路を通るため、次を実測で検証しています。

- レンダリング済みネットリストがプロセスに届くこと、掃引値が反映されること
- `.FILE` の書き換えと CSV の読み戻し（時間列の 10^12 倍を含む）
- 中間ファイルの分離（32 並列で互いの値を踏まないこと）— PID 依存を排した箇所の回帰テスト
- 実行後に作業ディレクトリが残らないこと、実行ファイルが無い場合にハングせず例外になること

実物のシミュレータを使う場合はコマンドを指定します。指定が無ければ該当の IT はスキップされます。

```bash
./gradlew test -Dmarginx.it.josim=josim -Dmarginx.it.jsim=jsim
```

**実 JoSIM が `.FILE` ディレクティブで出力先を決めるか**は、かつて未検証事項でしたが、
JoSIM 2.6.8 で**決めることを実測で確認済み**です（`.FILE` で指定した名前のファイルが作業ディレクトリに
生成されます）。`-o` を渡す形への変更は不要でした。JSIM も同じく `.FILE` を尊重します。
