# MarginXJ

[MarginX](https://github.com/Yamanashi-laboratory/MarginX)（C++）を、レイヤードアーキテクチャ + 依存性逆転（DIP）で Java に移植するプロジェクトです。

## レイヤ構成

依存方向は `presentation → application → domain ← infrastructure` の一方向のみ。
`domain/port` にインタフェースを置き、`infrastructure` がそれを実装します。

```
com.ynu.marginx
├─ presentation/          UI。引数なし or --gui で GUI、それ以外は CLI
│   ├─ MarginX                起動の振り分け（Application を継承しない）
│   ├─ cli/                   MarginXCommand, InteractiveMenu, OperationMode, ScoreChoice
│   │   └─ view/              ProgressBarView, MarginChartView, DetailView
│   └─ gui/                   MarginXFxApplication, MainWindow
│       ├─ editor/            NetlistHighlighter, NetlistEditor, NetlistValidator,
│       │                    ElementListView, EditorPane (RichTextFX)
│       ├─ result/            MarginChartData, MarginChartView, MarginTableView
│       ├─ task/              MarginCalculationTask
│       └─ export/            ResultExporter (PNG / CSV)
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
│   │                       SimulatorRegistry, ProcessExecutor, SimulatorWorkspaces
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
| `main.cpp`, `menu.cpp`, `display_*.cpp` | `presentation/cli/` |
| `fig_out.cpp`, `detail_out.cpp`, `Margin.cpp` のインジケータ | `presentation/cli/view/` |
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
| `margin_py.cpp`, `scripts/margin.py` | `presentation/gui/result/`（JavaFX Charts。Python は使いません） |
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
| 7. グラフ出力（`margin_py.cpp` の matplotlib を JavaFX GUI に置き換え） | マージン表示まで完了 |

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

**Windows には起動ファイルが 2 つ入ります。**

| 実行ファイル | 用途 |
|---|---|
| `MarginXJ.exe` | ウィンドウ。ダブルクリックで起動し、コンソールは開きません |
| `MarginXJ-cli.exe` | コマンドライン。`MarginXJ-cli MUX_clked -m 3` のように使います |

Windows では 1 つの実行ファイルがコンソールを持つかどうかを実行前に決めるため、両立できません。
GUI 利用者に黒いコンソールが付いてこないこと、CLI 利用者が結果を読めること、その両方を満たすには
起動口を分ける必要があります。Linux の `bin/MarginXJ` は 1 つで両方を兼ねます。

macOS 向けの配布物と、x86_64 以外（Linux arm64 など）向けの配布物は現時点ではありません。
これらの環境では「ビルドと実行（開発者向け）」の手順でソースからビルドしてください。

### GUI

引数なしで起動するとウィンドウが開きます（`--gui` でも同じ）。回路ファイルを選んで実行すると、
素子ごとの結果が終わったそばから表に積まれ、進捗バーが進みます。計算は別スレッドで走るので
操作は固まりません。中止ボタンで途中で止められ、止めた時点までの結果はそのまま残ります。

```bash
MarginXJ
```

- **マージン図** — 素子ごとに下限〜上限を 1 本の横棒で表示します（`scripts/margin.py` と同じ形）。
  表の行を選ぶと対応する棒が濃く変わり、棒をクリックすると表の行が選ばれます。
  **表もグラフもネットリストの素子順に並びます。** 探索は素子ごとに並列で走るため結果は
  終わった順に届きますが、表はその順ではなく素子の位置に従って行を挿入します。
- **使用中のシミュレータ**を常時表示します。JSIM に切り替わっている場合は警告色になり、
  理由がツールチップで出ます。
- **エクスポート** — グラフは PNG、結果は CSV（CLI と同じ形式・同じ出所コメント付き）。
- **Netlist タブ**は回路と判定ファイルを左右に並べて編集でき、右に素子一覧が出ます。
  - 素子行・ドットコマンド・MarginX 独自ディレクティブ（`*MIN` `*MAX` `*FIX` `*SYN` `*LMIN` などと、
    シャント行の `*SHUNT` `*Bc` `*calc`）・コメントを色分けします。認識する素子記号は `ElementType`
    から導いており、パーサと語彙がずれません。
  - 入力が止まると `NetlistParser` を呼んで検証します。**エディタ専用の構文解析は持ちません**
    （「エディタでは通るのに実行するとエラー」を原理的に排除するため）。失敗した行は赤で示します。
  - **マージン対象の素子行に色を付けます。** 対象かどうかはパーサが返した素子の行番号から決めており、
    「小文字始まりだけが対象」という分かりにくい規則が画面上で見えます。
  - 素子一覧の行をクリックすると該当行へ移動します。
  - 判定ファイルは回路と同じベース名（`adder.cir` なら `adder.txt`）に固定され、保存時もその名前です。
    計算が探す名前と食い違う保存はできません。

**最適化モード（CMM / CGM / 逐次 CGM）も同じ画面から実行できます。** モード欄で選ぶと、
CGM の 2 種のときだけ「何を最大化するか」の選択欄が現れます（CMM は最も厳しい素子を中央へ寄せる
方法なので、選ぶものがありません）。最適化は「回路を動かして測り直す」の繰り返しなので、
測り直しが終わるたびに表とグラフがその時点の回路に更新されます。進捗はサイクル数で表示し
（既定は最大 500 サイクル。歩留まりが伸びなくなればそれより早く終わります）、中止ボタンは
実行中のシミュレータまで止めます。結果の回路は CLI と同じく `<回路名>_out.cir` に書き出されます。

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

GUI では上部の **Simulators...** ボタンから同じ設定を行えます。JoSIM と JSIM それぞれについて
「見つかったかどうか」と「上の 1〜5 のどれで見つかったか」を表示し、実行ファイルを選び直すことも、
保存済みのパスを消して PATH の探索に戻すこともできます。保存先は CLI と同じ設定ファイルです。

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

- `./gradlew build` は **BUILD SUCCESSFUL**、テスト **148 件すべて通過**（JDK 21.0.12、
  実 JoSIM と実 JSIM を指定した状態で計測。指定が無い場合は IT 4 件がスキップされます）。
  `org.openjfx.javafxplugin` 0.1.0 は Gradle 9 でも問題なく動作します。
- **実 JoSIM 2.6.8 に対する `RealJosimIT` が通過。** かねてより未検証だった
  「実 JoSIM が `.FILE` で出力先を決めるか」は**決める**ことを実測で確認しました。`-o` への変更は不要です。
- **実 JSIM に対する `RealJsimIT` が通過。**
- CMM（モード 5）は実 JoSIM で通しで動作し、`<回路名>_out.cir` を出力することを確認済み。
- **GUI。** 引数なし / `--gui` のいずれでもウィンドウが開くこと、CLI が従来どおり動くことを実測。
  実 JoSIM で測ったマージンを本番のチャートに描かせ、PNG / CSV 出力まで確認しました。
- **jpackage の成果物（app-image）。** 生成した実行ファイルで、同梱ランタイムのみ（JDK なし）で
  CLI が `test_circuits/MUX_clked.cir` を判定できること、`--gui` でウィンドウが開くことを確認しました。
  JavaFX は fat jar 経由で読み込めており、`--module-path` や jlink ランタイムは不要でした。
- **エディタ。** 実回路 `MUX_clked.cir`（サブ回路・`*SYN`・範囲ディレクティブを含む）で、
  33 個のマージン対象の検出、対象行の強調、素子一覧、判定ファイルの同時表示を確認しました。
- **Ctrl+C による中断。** 計算中に実際のコンソール制御イベントを送り、5 個開いていた作業ディレクトリが
  0 になること、シミュレータのプロセス（ラッパー経由の孫プロセスを含む）が残らないことを実測しました。
- **`.msi` と `.deb` の生成、およびリリース CI。** `v0.1.0-rc2` で `release.yml` を実行し、
  2 分 54 秒で成功、4 つのアセットが公開されました。アクション更新後に `workflow_dispatch` で
  再実行し、**警告 0 件**（以前は setup-java の非推奨と Node 20 の非推奨が各 2 件）、
  Windows のキャッシュ保存も `Failed to save` なしで完了することを確認しています。
- **`v0.1.0` の正式リリース。** `release.yml` が 3 ジョブとも成功し、`.msi`（63 MB）、
  `.deb`（46 MB）、portable zip 2 種が公開されました。警告 0 件で、`publish` ジョブでのみ動く
  `actions/download-artifact@v7` もここで初めて実行され、問題なく動作しています。
- **ライセンス表示の同梱。** 公開された `.deb` を `dpkg-deb -c` で検査し、
  `/opt/marginxj/lib/LICENSE`（1074 B）と `/opt/marginxj/lib/THIRD-PARTY-NOTICES.md`（3480 B）が
  含まれることを確認しました。`.msi` は `msiexec /a` で展開して同様に確認済みです。
- **`--version` がビルドの版番号を返すこと。** `-Pversion=9.9.9` でビルドした fat jar が
  `MarginXJ 9.9.9` を返すことを実測しました。
- **GUI からの最適化の長時間実行。** `GuiOptimizationIT` が、実 JoSIM と `MUX_clked` に対して
  ウィンドウから CGM を既定設定のまま完走させます。実測は 959 秒と 546 秒（CGM は乱数依存で
  サイクル数が毎回変わります）。ステータス行が停止理由を報告すること、`MUX_clked_out.cir` が
  書き出されること、表に 33 素子すべてが並ぶことまで確認します。
  画面を座標でクリックするのではなく `MainWindow` を JavaFX スレッド上で直接駆動するので、
  無人で再実行できます。`-Dmarginx.it.josim` を付けたときだけ動きます。
- **テストの並行実行耐性。** ビルドがテスト JVM に専用の temp（`build/tmp/test-workspaces`）を
  与えるため、同じ端末で別の MarginXJ が動いていても影響を受けません。以前 3 件を落とした条件
  （実 JoSIM の CGM を走らせたままテスト全体を実行し、システム temp に他プロセスの作業
  ディレクトリが 28 件ある状態）を再現し、**151 件すべて通過**することを確認しました。
- **最適化モードの実回路での収束（既定設定）。** 実 JoSIM + `test_circuits/MUX_clked.cir`
  （マージン対象 33 個 / 判定 51 件）を、設定を縮めずそのまま回した結果です。

  | モード | 所要時間 | 停止理由 | Critical Margin |
  | --- | --- | --- | --- |
  | 5 CMM | 68 秒 | 1 試行目で同じ素子が再び最厳 | 16.41 % → **16.99 %** |
  | 6 CGM | 3 分 4 秒 | 105 サイクルで歩留まりが停滞 | 16.41 % → **17.77 %** |
  | 7 逐次 CGM | 4 分 23 秒 | 105 サイクルで歩留まりが停滞 | 16.41 %（入力のまま） |

  1 シミュレーションが約 0.2 秒のため、CGM の上限 500 × 100 = 50,000 回でも現実的な時間で終わります。
  **CGM の 2 種は乱数に依存するので、上の数値は実行ごとに変わります。** 逐次 CGM がこの回で
  改善しなかったのは異常ではありません。逐次版は spread を広げる条件が「閾値より大きい」と
  厳しく、広げた時にしか再測定・最良更新が起きないため、更新機会そのものが少なくなります
  （この回の再測定は 4 回）。一度も更新できないとき入力回路をそのまま返すのは、
  [意図的に修正した不整合](#移植にあたり修正した元コードの不整合) の 4 番目の挙動です。
  また CMM は乱数を使わないため決定的で、リファクタリング前後で 4 つの出力ファイルが
  バイト単位で一致することを確認しています。

未検証:

- **C++ 版との数値一致（最適化モード）。** CGM の 2 種は乱数に依存するため、C++ 版と数値が
  一致することは原理的にありません（元コード自身も `random_device` で毎回異なる結果を出します）。
  比較できるのは「どちらも歩留まりが停滞したら止まる」といった振る舞いのみです。
- **CI での実シミュレータのテスト。** `RealJosimIT` / `RealJsimIT` の 4 件は CI では
  スキップされます（両 OS で 144 件通過 / 4 件スキップ）。JoSIM には apt パッケージが無く、
  ソースからのビルドが必要なためです。これらは手元の実 JoSIM / 実 JSIM で確認しています。

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
- `NetlistValidatorTest` / `EditorPaneTest` — エディタの検証・対象行の印・保存。
  `test_circuits/MUX_clked.cir`（実回路）に対して、対象 33 個の検出と行番号の一致を確認
- `NetlistHighlighterTest` — 色分けの規則。素子記号は `ElementType` 全種を走査し、
  `*MIN` のようなディレクティブと単なるコメントの区別を固定。JavaFX 非依存
- `MarginChartDataTest` — グラフの軸範囲・並び順・棒の幅。JavaFX に依存しない純粋なクラス
- `MarginResultViewsTest` / `MarginCalculationTaskTest` — 実際のシーングラフに対する表とグラフの
  選択連動、結果の逐次追加、中止。ツールキットが無い環境ではスキップされます
- `CancellationTest` — 中断してもシミュレータのプロセスと一時ディレクトリが残らないこと、
  素子ごとの進捗が通知されること。スタブに遅延を入れ、シミュレーション実行中に中断させて検証
- `OptimizeCircuitUseCaseTest` / `OptimizationTaskTest` — 最適化の進捗通知とキャンセル。
  サイクル・再測定が数えられること、中断時に何も書き出さないこと
- `MainWindowTest` — ウィンドウが提供するモード一覧と、CGM の 2 種を選んだときだけ
  スコア選択が現れること
- `GuiOptimizationIT` — ウィンドウから最適化を 1 回完走させる統合テスト。既定ではスキップ
- `RealJosimIT` / `RealJsimIT` — 実シミュレータに対するテスト。既定ではスキップ

### CI

`.github/workflows/ci.yml` が push と pull request のたびに `ubuntu-22.04` と `windows-2022` の
両方で `./gradlew build` を回します。ここまでで実際に踏んだ不具合の多くが OS 固有だったためです
（Windows がファイルハンドルを離さない、後始末が片方でだけ失敗する、など）。

**Linux では `xvfb-run` を通します。** ディスプレイが無いと JavaFX を使うテストは失敗ではなく
スキップになるため、そのままでは「緑だが実は検証していない」状態になります。

`release.yml` も、パッケージング前に同じ `./gradlew build` を実行します。タグ push では
`ci.yml` は走らないので、そこで別途テストしない限り「テストが落ちる状態のインストーラが
公開される」経路が残るためです。

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

## ライセンス

MarginXJ は **MIT ライセンス**です。全文は [LICENSE](LICENSE) にあります。

移植元の [MarginX](https://github.com/Yamanashi-laboratory/MarginX)（C++、2023 年）も同じ作者に
よるもので、MarginXJ はその Java 移植にあたります。`test_circuits/` の回路と判定ファイルも
MarginX 由来の資産で、同じライセンスに含まれます。

**シミュレータは MarginXJ に含まれません。** JoSIM は MIT ライセンスで、
[JoSIM の Releases](https://github.com/JoeyDelp/JoSIM/releases) から各自で入手してください。
JSIM には明示的なライセンス文書がなく、これが同梱しない判断の理由の一つです
（[ADR 0001](docs/adr/0001-distribution-strategy.md)）。

配布物（`.msi` / `.deb` / portable zip / fat jar）には JavaFX・RichTextFX・picocli と Java
ランタイムが含まれます。それぞれの著作権表示は
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) にまとめています。RichTextFX 系の
BSD 2-Clause がバイナリ配布での表示再掲を求めているためです。

**この 2 つのファイルは配布物そのものに同梱されます。** リポジトリに置くだけでは条件を
満たさないためで、インストール後の場所は OS で異なり、Windows は `MarginXJ.exe` の隣、Linux は
`/opt/marginxj/lib/` の下です（jpackage が `--app-content` を置く場所の違い）。
fat jar ならアーカイブのルートに入っています。
依存を追加・更新したときは `THIRD-PARTY-NOTICES.md` も更新してください。
