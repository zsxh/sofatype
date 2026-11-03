# sofatype 输入法开发环境
#
# 原则：本项目最少必须的开发工具全部在此声明，保证跨机器可复现；
# 不依赖宿主机已安装的任何开发工具（即使本机已有，也照样声明）。
# 开发中需要新工具时，先加入本文件再使用。不在系统上手工安装 SDK。
#
# 用法：
#   nix develop            进入开发 shell（JDK / Android SDK / adb / git / gradle 全部就绪）
#   nix run .#tv-avd       创建 Android TV 模拟器（首次执行一次）
#   nix run .#tv-emulator  启动 TV 模拟器
{
  description = "sofatype 输入法开发环境：JDK 17 + Android SDK（compileSdk 34 / minSdk 23）+ Android TV 模拟器（API 34）";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";

  outputs =
    { self, nixpkgs }:
    let
      # 支持的宿主机。Nix 按当前机器架构自动选择对应输出（即"取 host 架构"）：
      # 每个系统各生成一份 devShell / apps；列表外的机器会明确报错，不会静默猜。
      systems = [
        "aarch64-darwin" # 开发机：Apple Silicon Mac
        "aarch64-linux" # ARM Linux（模拟器需 /dev/kvm，用户需在 kvm 组）
        "x86_64-linux" # x86_64 Linux（同需 /dev/kvm；模拟器镜像回退 x86，见下）
      ];
      forAllSystems = nixpkgs.lib.genAttrs systems;

      env =
        system:
        let
          # 不用 legacyPackages：需要注入 nixpkgs config。
          # nixos-26.05 起 Android SDK 许可证接受移到 androidenv 层，
          # 由 config.android_sdk.accept_license 声明（composeAndroidPackages
          # 不再收 licenseAccepted 参数，传了会直接报错）。
          pkgs = import nixpkgs {
            inherit system;
            config = {
              android_sdk.accept_license = true;
              # SDK 组件（cmdline-tools / emulator / 系统镜像）在 nixpkgs 标记为
              # unfree 许可证；本 flake 仅声明本地开发环境，接受其许可
              allowUnfree = true;
            };
          };

          # 构建用 JDK：darwin 上解析为 Zulu 17 预编译，Linux 上为 OpenJDK 17
          # （AGP 8.x 要求 JDK 17+）
          jdk = pkgs.jdk17;

          # TV 模拟器镜像 ABI 跟随宿主机架构：
          #   aarch64（darwin/linux）→ arm64-v8a（原生）；
          #   x86_64 → x86（android-tv 系列没有 x86_64 镜像）
          tvAbi = if pkgs.stdenv.hostPlatform.isAarch64 then "arm64-v8a" else "x86";

          androidEnv = pkgs.androidenv.composeAndroidPackages {
            # 兼容基线：minSdk 23（PRD 第 12 章决策 13，验收机小米电视4 / Android 6.0.1）；
            # compileSdk 用 34
            platformVersions = [ "34" ];
            buildToolsVersions = [ "34.0.0" ];
            # 依赖均为纯 Java/Kotlin（含 ZXing core，无原生代码）：不装 NDK / CMake
            includeNDK = false;
            includeCmake = false;
            includeSources = false;
            # TV 模拟器镜像：system-images;android-34;android-tv;${tvAbi}；
            # 该镜像用标准 android-sdk-license，无需预览许可
            includeEmulator = true;
            includeSystemImages = true;
            systemImageTypes = [ "android-tv" ];
            abiVersions = [ tvAbi ];
          };

          sdk = androidEnv.androidsdk;
        in
        {
          inherit pkgs jdk tvAbi;
          inherit sdk;
        };
    in
    {
      devShells = forAllSystems (
        system:
        let
          a = env system;
        in
        {
          default = a.pkgs.mkShell {
            packages = [
              a.jdk
              a.sdk
              a.pkgs.git # 版本管理必须工具，不依赖宿主机已有的 git
              a.pkgs.gradle # 仅用于首次 `gradle wrapper` 生成包装器；日常构建用 ./gradlew
            ];

            shellHook = ''
              export JAVA_HOME="${a.jdk.home}"
              export ANDROID_HOME="${a.sdk}/libexec/android-sdk"
              export ANDROID_SDK_ROOT="$ANDROID_HOME"
              # sdk 包自带统一 bin/（avdmanager、sdkmanager、adb、emulator 都在）
              export PATH="$JAVA_HOME/bin:${a.sdk}/bin:$ANDROID_HOME/emulator:$PATH"

              echo "sofatype 开发环境已就绪（${system}）"
              echo "  构建：            ./gradlew assembleDebug（首次可先 gradle wrapper 生成包装器）"
              echo "  创建 TV 模拟器：  nix run .#tv-avd"
              echo "  启动 TV 模拟器：  nix run .#tv-emulator"
            '';
          };
        }
      );

      apps = forAllSystems (
        system:
        let
          a = env system;
          mkApp = name: script: {
            type = "app";
            program = toString (a.pkgs.writeShellScript name script);
          };
          avdName = "sofatype-tv";
          sysimg = "system-images;android-34;android-tv;${a.tvAbi}";
          common = ''
            # 出错立即退出，不打印误导性的成功文案
            set -euo pipefail
            export JAVA_HOME="${a.jdk.home}"
            export ANDROID_HOME="${a.sdk}/libexec/android-sdk"
            export ANDROID_SDK_ROOT="$ANDROID_HOME"
            export PATH="$JAVA_HOME/bin:${a.sdk}/bin:$ANDROID_HOME/emulator:$PATH"
          '';
        in
        {
          # 创建 TV 模拟器（首次使用执行一次；--force 会覆盖同名旧 AVD）
          tv-avd = mkApp "tv-avd" ''
            ${common}
            echo no | avdmanager create avd \
              --force \
              --name ${avdName} \
              --package "${sysimg}" \
              --device "tv_1080p"
            echo "AVD ${avdName} 已创建（tv_1080p 形态，1080p 电视）。启动：nix run .#tv-emulator"
          '';

          # 启动 TV 模拟器。遥控器对应：键盘方向键=十字键，回车=OK，退格=返回
          tv-emulator = mkApp "tv-emulator" ''
            ${common}
            exec emulator -avd ${avdName}
          '';
        }
      );
    };
}
