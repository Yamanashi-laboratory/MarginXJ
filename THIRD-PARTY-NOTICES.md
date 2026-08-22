# サードパーティのライセンス表示

MarginXJ の配布物（`.msi` / `.deb` / portable zip / fat jar）には、次のライブラリと Java
ランタイムが含まれています。**BSD 2-Clause は、バイナリ配布の際に著作権表示と免責条項を
再掲することを求めています。** この文書がその再掲にあたります。

なお **JoSIM と JSIM は同梱していません。** 利用者が別途入手するもので、MarginXJ の配布物には
含まれないため、ここには挙げていません（[ADR 0001](docs/adr/0001-distribution-strategy.md)）。

## RichTextFX とその依存

ネットリストエディタが使っています。

- `org.fxmisc.richtext:richtextfx` 0.11.2
- `org.fxmisc.flowless:flowless` 0.7.2
- `org.fxmisc.undo:undofx` 2.1.1
- `org.fxmisc.wellbehaved:wellbehavedfx` 0.3.3
- `org.reactfx:reactfx` 2.0-M5

いずれも **BSD 2-Clause License**（各 POM の `<licenses>` で宣言）。

```
Copyright (c) 2013-2017, Tomas Mikula and contributors
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## picocli

コマンドラインの解析に使っています。

- `info.picocli:picocli` 4.7.6 — **Apache License 2.0**
  （<https://www.apache.org/licenses/LICENSE-2.0>）

## JavaFX (OpenJFX)

ウィンドウの描画に使っています。

- `org.openjfx:javafx-base` / `javafx-graphics` / `javafx-controls` / `javafx-fxml` /
  `javafx-swing` 21.0.5 — **GPL v2 with the Classpath Exception**
  （<https://openjdk.org/legal/gplv2+ce.html>）

Classpath Exception により、これらとリンクする MarginXJ 自身のコードに GPL は及びません。

## 同梱の Java ランタイム

インストーラと portable zip には、jpackage が生成した Java ランタイムが含まれます。ビルドに
使用した JDK の配布元のライセンス（Eclipse Temurin の場合は **GPL v2 with the Classpath
Exception**）に従います。

---

上記のバージョンは `gradle/libs.versions.toml` と `runtimeClasspath` の実際の解決結果に基づき、
ライセンスは各成果物の POM 宣言を確認したものです。依存を追加・更新したときは、この文書も
合わせて更新してください。
