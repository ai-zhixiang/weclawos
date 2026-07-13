#!/bin/bash
# ==================================================
# 微侠桌面 v2 — Cage + 浏览器 Kiosk 构建脚本
# Alpine Linux + Cage + Chromium + 全屏 HTML UI
# ==================================================
set -e

# 配置
ALPINE_VERSION="3.21"
MIRROR="${MIRROR:-https://mirrors.tuna.tsinghua.edu.cn/alpine}"
ROOTFS="${ROOTFS:-$HOME/alpine-desktop/build2}"
OUTPUT_IMG="${OUTPUT_IMG:-$HOME/alpine-desktop/weclaw-desktop-v2.iso}"
IMG_SIZE="${IMG_SIZE:-3G}"
DEMO_HTML="/mnt/shared/projects/微侠桌面/demo/weclaw-desktop-demo.html"
AUDIO_URL="https://dl-cdn.alpinelinux.org/alpine/v3.21/community/x86_64"

RED='\033[0;31m'; GREEN='\033[0;32m'; NC='\033[0m'
info() { echo -e "${GREEN}[✓]${NC} $1"; }
err()  { echo -e "${RED}[✗]${NC} $1"; }

cleanup() {
    sudo umount "$ROOTFS/proc" 2>/dev/null || true
    sudo umount "$ROOTFS/sys" 2>/dev/null || true
    sudo umount "$ROOTFS/dev/pts" 2>/dev/null || true
    sudo umount "$ROOTFS/dev" 2>/dev/null || true
}
trap cleanup EXIT

# =============================================
# Step 1: apk-tools static
# =============================================
if [ ! -f "apk" ]; then
    info "下载 apk-tools static..."
    wget -q "$MIRROR/v$ALPINE_VERSION/main/x86_64/APKINDEX.tar.gz" -O /tmp/APKINDEX.tar.gz
    APK_VER=$(tar -xzf /tmp/APKINDEX.tar.gz -O APKINDEX 2>/dev/null | grep -A1 'P:apk-tools-static' | grep 'V:' | cut -d: -f2 | tr -d ' ')
    wget -q "$MIRROR/v$ALPINE_VERSION/main/x86_64/apk-tools-static-2.14.6-r3.apk" -O /tmp/apk-tools.apk
    tar -xzf /tmp/apk-tools.apk -C .
    cp sbin/apk.static apk && chmod +x apk
    info "apk-tools $APK_VER"
fi

# =============================================
# Step 2: 最小化根文件系统
# =============================================
info "构建微侠桌面 v2 根文件系统..."
sudo rm -rf "$ROOTFS"
mkdir -p "$ROOTFS/etc/apk" "$ROOTFS/var/cache/apk" "$ROOTFS/dev" "$ROOTFS/proc" "$ROOTFS/sys"

cat > "$ROOTFS/etc/apk/repositories" << REPO
$MIRROR/v$ALPINE_VERSION/main
$MIRROR/v$ALPINE_VERSION/community
REPO

info "安装核心包: linux-virt, cage, chromium, 音频..."
sudo ./apk --root "$ROOTFS" --allow-untrusted --initdb add --quiet \
    alpine-baselayout alpine-keys alpine-release \
    busybox openrc eudev \
    linux-virt linux-firmware-none \
    seatd cage \
    chromium \
    mesa-dri-gallium mesa-egl mesa-va-gallium \
    python3 \
    alsa-utils alsa-ucm-conf \
    pulseaudio pulseaudio-alsa pulseaudio-utils \
    dbus elogind \
    curl dhcpcd doas font-noto-cjk \
    2>&1 | tail -5

# =============================================
# Step 3: 系统配置
# =============================================
info "配置系统..."

# 用户
sudo chroot "$ROOTFS" /bin/sh -c '
    adduser -D -h /home/weclaw -s /bin/sh weclaw
    for g in video input seat audio pulse pulse-access; do addgroup weclaw $g; done
'
echo 'weclaw:weclaw' | sudo chroot "$ROOTFS" chpasswd

# Make rootfs writable for config (no sudo needed for each write)
sudo chown -R $(id -u):$(id -g) "$ROOTFS/"

# 主机名
echo 'weclaw' > "$ROOTFS/etc/hostname"

# 网络
cat > "$ROOTFS/etc/network/interfaces" << NET
auto lo
iface lo inet loopback
auto eth0
iface eth0 inet dhcp
NET

# seatd
echo 'SEATD_OPTS="-g seat"' > "$ROOTFS/etc/conf.d/seatd"

# 自动登录 tty1
sed -i 's|tty1::respawn:/sbin/getty 115200 tty1|tty1::respawn:/sbin/getty -a weclaw 115200 tty1|' \
    "$ROOTFS/etc/inittab" 2>/dev/null || true

# OpenRC runlevels
for svc in seatd dhcpcd local dbus pulseaudio; do
    sudo chroot "$ROOTFS" ln -sf /etc/init.d/$svc /etc/runlevels/default/ 2>/dev/null || true
done
for svc in hostname hwdrivers modules; do
    sudo chroot "$ROOTFS" ln -sf /etc/init.d/$svc /etc/runlevels/boot/ 2>/dev/null || true
done
for svc in mount-ro killprocs savecache; do
    sudo chroot "$ROOTFS" ln -sf /etc/init.d/$svc /etc/runlevels/shutdown/ 2>/dev/null || true
done

# pulseaudio 开机启动
sudo chroot "$ROOTFS" rc-update add pulseaudio default

# =============================================
# Step 4: WeClaw UI 配置
# =============================================
info "安装微侠全屏 UI..."

# 用户目录结构
mkdir -p "$ROOTFS/home/weclaw/.config/cage"
mkdir -p "$ROOTFS/home/weclaw/.local/bin"
mkdir -p "$ROOTFS/home/weclaw/weclaw-ui"

# ----- 复制 demo UI -----
# 用内联方式创建一个完整的全屏UI HTML
cp /home/ubuntu/alpine-desktop/weclaw-ui.html "$ROOTFS/home/weclaw/weclaw-ui/index.html"

# ----- UI 服务器脚本（busybox httpd，极轻） -----
cat > "$ROOTFS/home/weclaw/.local/bin/serve-ui" << 'SERVE'
#!/bin/sh
# 启动微侠 UI HTTP 服务器
cd /home/weclaw/weclaw-ui
busybox httpd -f -p 8080 -h /home/weclaw/weclaw-ui
SERVE
chmod +x "$ROOTFS/home/weclaw/.local/bin/serve-ui"

# ----- Cage 配置 — 自动启动 Chromium kiosk -----
cat > "$ROOTFS/home/weclaw/.config/cage/cage.conf" << CAGECFG
CAGE_KIOSK=1
CAGE_KEEP_BG=0
CAGECFG

# ----- .profile — 自动启动 Cage + Chromium kiosk -----
cat > "$ROOTFS/home/weclaw/.profile" << 'PROFILE'
if [ "$(tty)" = "/dev/tty1" ]; then
    export XDG_RUNTIME_DIR=/tmp/runtime-weclaw
    export WAYLAND_DISPLAY=wayland-1
    export CHROMIUM_FLAGS="--kiosk --no-first-run --disable-features=Translate --disable-fre --no-default-browser-check --disable-sync"
    export PULSE_SERVER=unix:/tmp/pulse-socket
    mkdir -p "$XDG_RUNTIME_DIR" && chmod 700 "$XDG_RUNTIME_DIR"

    # 启动 UI 服务器
    serve-ui &

    sleep 2
    # Cage = 全屏 kiosk 合成器，只跑 Chromium
    exec cage chromium $CHROMIUM_FLAGS http://localhost:8080
fi
PROFILE

# ----- motd -----
cat > "$ROOTFS/etc/motd" << 'MOTD'
╔══════════════════════════════════╗
║    🦞 微侠桌面 v2                ║
║    Alpine + Cage + Chromium      ║
║                                  ║
║    全屏 AI 语音桌面               ║
║    开机即用，说吧                  ║
╚══════════════════════════════════╝
MOTD

# 权限
sudo chown -R 1000:1001 "$ROOTFS/home/weclaw"

# =============================================
# Step 5: 创建 ISO 映像
# =============================================
info "创建 ISO 映像 $OUTPUT_IMG..."

# 安装 syslinux/grub
info "安装引导..."
sudo ./apk --root "$ROOTFS" --allow-untrusted add --quiet \
    syslinux grub grub-bios 2>/dev/null || true

# 创建 ext4 磁盘映像
dd if=/dev/zero of="$OUTPUT_IMG" bs=1M count=0 seek=3000 2>/dev/null || \
    fallocate -l 3G "$OUTPUT_IMG"

# 分区
cat << FDISK | sudo fdisk "$OUTPUT_IMG" > /dev/null 2>&1
o
n
p
1
2048
+512M
n
p
2

t
1
83
a
1
w
FDISK

LOOP_DEV=$(sudo losetup -fP --show "$OUTPUT_IMG")

sudo mkfs.ext4 -F "${LOOP_DEV}p1" > /dev/null 2>&1
sudo mkfs.ext4 -F "${LOOP_DEV}p2" > /dev/null 2>&1

MNT=$(mktemp -d)
sudo mount "${LOOP_DEV}p2" "$MNT"
sudo cp -a "$ROOTFS/." "$MNT/"

sudo mount "${LOOP_DEV}p1" "$MNT/boot"
sudo cp "$ROOTFS/boot/"* "$MNT/boot/" 2>/dev/null || true

# extlinux 引导
sudo extlinux --install "$MNT/boot" 2>/dev/null || info "extlinux 未安装，跳过"
cat > "$MNT/boot/extlinux.conf" << 'EXTLINUX'
DEFAULT weclaw
LABEL weclaw
    LINUX /vmlinuz-virt
    INITRD /initramfs-virt
    APPEND root=/dev/sda2 console=tty1 quiet
EXTLINUX

# MBR
dd if=/usr/lib/syslinux/mbr.bin of="${LOOP_DEV}" bs=440 count=1 2>/dev/null || true

sync
sudo umount "$MNT/boot" 2>/dev/null || true
sudo umount "$MNT" 2>/dev/null || true
sudo losetup -d "$LOOP_DEV" 2>/dev/null || true
rmdir "$MNT" 2>/dev/null || true

cleanup

info "🎉 微侠桌面 v2 映像已创建: $OUTPUT_IMG"
echo ""
echo "启动测试:"
echo "  qemu-system-x86_64 -m 2G -drive file=$OUTPUT_IMG,format=raw -vga virtio -display gtk -audiodev alsa,id=alsa0"
echo ""
echo "写入U盘:"
echo "  sudo dd if=$OUTPUT_IMG of=/dev/sdX bs=4M status=progress"
