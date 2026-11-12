# 日本語の手動 Call Screen

標準搭載の Google 電話を既定の電話アプリに設定し、日本の SIM と日本語で使う構成が対象です。Nothing Phone (2a) の Nothing OS 4.1（Android 16）と Google 電話 246.0.1012442248-publicbeta で、フラグの適用と応答音声の取得を確認しました。

## インストール

1. KernelSU Next で [CallScreen-v3.zip](https://github.com/onokatio/Pixelify-NEXT-Submodules/releases/download/callscreen-v3/CallScreen-v3.zip) をインストールしてください。
2. 端末を再起動し、ロックを解除してください。モジュールが手動スクリーニングに必要なフラグを適用します。
3. Google 電話の［設定］→［通話スクリーニング］を開いてください。日本語の応答音声がまだ入っていない場合は［再試行］を押してダウンロードしてください。

## Google 電話の更新

モジュールは起動後も設定キャッシュを確認し、Google が設定を再配信した場合にフラグを適用し直します。アプリの更新後は APK 内のフラグ名とキャッシュの形式を確認してから書き込みます。

Google が仕様を変更した場合はモジュールの更新が必要になることがあります。処理結果は `adb logcat -s PixelifyCallScreen` で確認できます。

## ビルド

JDK 21 でリポジトリのルートから `./gradlew :CallScreen:check :CallScreen:buildZip --configure-on-demand` を実行してください。ZIP は `CallScreen/out/CallScreen-v3.zip` に出力されます。
