# sofatype 输入法开发环境
#
# 原则：本项目最少必须的开发工具全部在此声明，保证跨机器可复现；
# 不依赖宿主机已安装的任何开发工具（即使本机已有，也照样声明）。
# 开发中需要新工具时，先加入本文件再使用。不在系统上手工安装 SDK。
#
# 用法：
#   nix develop            进入开发 shell（JDK / Android SDK / adb / git / gradle / obscura 全部就绪）
#   nix run .#tv-avd       创建 Android TV 模拟器（首次执行一次）
#   nix run .#tv-emulator  启动 TV 模拟器
# H5 页调试：`obscura serve` 起 CDP 端点，Puppeteer / Playwright 经 WebSocket 接入（详见 obscura 处注释）
{
  description = "sofatype 输入法开发环境：JDK 17 + Android SDK（compileSdk 34 / minSdk 23）+ Android TV 模拟器（API 34）+ Obscura 无头浏览器（H5 CDP 调试）";

  # 唯一 nixpkgs 输入，所有工具统一单 channel（2026-11-07 拍板：unstable 系，
  # 原 nixos-26.05 口径作废；Obscura 仅收录于 unstable 系，nixos-26.05 分支无此包）。
  # 形式（2026-11-07 复拍板）：github:NixOS/nixpkgs/nixpkgs-unstable —— nixpkgs-unstable
  # channel 的快照分支，纯包集门禁（不含 NixOS 测试），对 devShell 用途与 nixos-unstable 等价。
  # 注意：github: 输入仅在 `nix flake update` 时调 GitHub API 解析分支头，本机代理出口 IP
  # 共享限流，403 期间无法更新（日常构建/他人 clone 用已锁 flake.lock，不碰 API，不受影响）；
  # 持续 403 时可临时改回 channel tarball 形式锁定：channels.nixos.org/nixpkgs-unstable/nixexprs.tar.xz。
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";

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
              # Obscura 无头浏览器（Rust 引擎，内嵌 V8，Apache-2.0）：手机 H5 页（远程输入页）
              # 的 CDP 调试通道。`obscura serve --port 9222` 起服务后：
              #   Puppeteer：puppeteer.connect({ browserWSEndpoint: 'ws://127.0.0.1:9222' })
              #   Playwright：chromium.connectOverCDP('ws://127.0.0.1:9222')
              # 访问本机 / 局域网 H5 页（127.0.0.1 / 192.168.x）必须加 --allow-private-network：
              # 默认拦截回环与 RFC1918 目标（防抓取场景 SSRF）。默认仅监听 127.0.0.1 回环；
              # 非回环绑定需 OBSCURA_CDP_TOKEN（≥32 字节）。
              # 调试结束退出 serve，不残留监听端口（AGENTS.md 禁止事项 3）。
              a.pkgs.obscura
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
              echo "  H5 CDP 调试：      obscura serve --port 9222，Puppeteer/Playwright 接 ws://127.0.0.1:9222"
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
