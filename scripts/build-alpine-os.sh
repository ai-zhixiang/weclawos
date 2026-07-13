#!/usr/bin/env bash
set -euo pipefail

# ═══════════════════════════════════════════════════════════════
# 微侠桌面版 · Alpine OS 构建脚本
# 构建：Alpine Linux + River + Foot + 修复 initramfs
# ═══════════════════════════════════════════════════════════════

ROOT="$HOME/alpine-desktop"
BUILD="$ROOT/build"
ROOTFS="$BUILD/rootfs"
IMG="$ROOT/weclaw-v2.img"
IMG_SIZE="2G"
UUID_ROOT="d7bafe35-2dfb-4093-b266-02012a5398e9"
KERNEL_FLAVOR="lts"
ALPINE_MIRROR="https://mirrors.aliyun.com/alpine/v3.20"
ALPINE_VERSION="3.20.5"

mkdir -p "$BUILD" "$ROOTFS"

log() { echo "[$(date +%H:%M:%S)] $*"; }

# ── 0. 下载 Alpine minirootfs ──
log "📦 下载 Alpine $ALPINE_VERSION minirootfs..."
MINIROOTFS="$BUILD/alpine-minirootfs.tar.gz"
if [ ! -f "$MINIROOTFS" ]; then
  wget -q -O "$MINIROOTFS" \
    "$ALPINE_MIRROR/releases/x86_64/alpine-minirootfs-$ALPINE_VERSION-x86_64.tar.gz" || {
    # fallback: try latest-stable
    log "⚠️  $ALPINE_VERSION 不存在，尝试 latest-stable..."
    wget -q -O "$MINIROOTFS" \
      "https://mirrors.aliyun.com/alpine/latest-stable/releases/x86_64/alpine-minirootfs-3.21.0-x86_64.tar.gz"
  }
fi
log "✅ 下载完成: $(du -h "$MINIROOTFS" | cut -f1)"

# ── 1. 解压 rootfs ──
log "📂 解压 rootfs..."
sudo rm -rf "$ROOTFS"
mkdir -p "$ROOTFS"
sudo tar xzf "$MINIROOTFS" -C "$ROOTFS"
log "✅ 解压完成"

# ── 2. 配置 apk 源 ──
log "🔧 配置 apk 源..."
sudo mkdir -p "$ROOTFS/etc/apk"
echo "$ALPINE_MIRROR/main" | sudo tee "$ROOTFS/etc/apk/repositories" > /dev/null
echo "$ALPINE_MIRROR/community" | sudo tee -a "$ROOTFS/etc/apk/repositories" > /dev/null

# ── 3. 安装基础包 ──
log "📦 安装基础包..."
sudo chroot "$ROOTFS" /sbin/apk --no-cache add \
  alpine-base alpine-conf busybox busybox-openrc \
  linux-lts linux-firmware \
  e2fsprogs grub grub-bios \
  river foot lynx terminus-font \
  font-font-noto-cjk iwd dhcpcd \
  dbus elogind  \
  openssh sudo htop neofetch \
  bash curl git vim \
  2>&1 | tail -3
log "✅ 基础包安装完成"

# ── 4. 配置系统 ──
log "⚙️  配置系统..."

# 主机名
echo "weclavos" | sudo tee "$ROOTFS/etc/hostname" > /dev/null

# 网络
sudo mkdir -p "$ROOTFS/etc/network"
cat <<'NET' | sudo tee "$ROOTFS/etc/network/interfaces" > /dev/null
auto lo
iface lo inet loopback

auto eth0
iface eth0 inet dhcp
NET

# 时区
sudo ln -sf /usr/share/zoneinfo/Asia/Shanghai "$ROOTFS/etc/localtime"
echo "Asia/Shanghai" | sudo tee "$ROOTFS/etc/timezone" > /dev/null

# 创建用户
sudo chroot "$ROOTFS" adduser -D -s /bin/bash weclaw
echo "weclaw:weclaw" | sudo chroot "$ROOTFS" chpasswd
sudo chroot "$ROOTFS" adduser weclaw wheel

# 自动登录 tty1 到 weclaw 用户
sudo mkdir -p "$ROOTFS/etc/init.d"
cat <<'GETTY' | sudo tee "$ROOTFS/etc/init.d/agetty.tty1" > /dev/null
#!/sbin/openrc-run
command=/sbin/agetty
command_args="--autologin weclaw --noclear 38400 tty1 linux"
pidfile=/run/agetty.tty1.pid
GETTY
sudo chmod +x "$ROOTFS/etc/init.d/agetty.tty1"

# ── 5. 配置 River + 自动启动桌面 ──
log "🎨 配置 River 桌面..."

# .profile — 启动 River
cat <<'PROFILE' | sudo tee "$ROOTFS/home/weclaw/.profile" > /dev/null
#!/bin/sh
if [ -z "${DISPLAY}" ] && [ "${TTY}" = "tty1" ]; then
  # 启动 dbus
  dbus-run-session river &
  sleep 1
  # 桌面启动后打开终端
  foot &
  wait
fi
PROFILE
sudo chown -R 1000:1000 "$ROOTFS/home/weclaw"

# River 配置
sudo mkdir -p "$ROOTFS/home/weclaw/.config/river"
cat <<'RC' | sudo tee "$ROOTFS/home/weclaw/.config/river/init" > /dev/null
#!/bin/sh
# River 合成器配置
riverctl default-layout rivertile
riverctl keyboard-layout -options "ctrl:nocaps" us

# Super+Return → foot
riverctl map normal Super Return spawn foot
# Super+Q → 关闭窗口
riverctl map normal Super Q close
# Super+Shift+E → 退出 River
riverctl map normal Super+Shift E exit
RC
sudo chmod +x "$ROOTFS/home/weclaw/.config/river/init"
sudo chown -R 1000:1000 "$ROOTFS/home/weclaw"

# ── 6. 配置 openrc 服务 ──
log "🔧 配置开机自启..."
sudo chroot "$ROOTFS" rc-update add dbus default
sudo chroot "$ROOTFS" rc-update add dhcpcd default
sudo chroot "$ROOTFS" rc-update add iwd default
sudo chroot "$ROOTFS" rc-update add local default
sudo chroot "$ROOTFS" rc-update add agetty.tty1 default

# ── 7. 创建磁盘映像 + GRUB ──
log "💿 创建磁盘映像: $IMG..."
sudo rm -f "$IMG"
qemu-img create -f raw "$IMG" "$IMG_SIZE"

# 分区：MBR + ext4
sudo parted "$IMG" --script mklabel msdos
sudo parted "$IMG" --script mkpart primary ext4 1MiB 100%
sudo parted "$IMG" --script set 1 boot on

# 挂载循环设备
LOOP=$(sudo losetup -fP --show "$IMG")
log "🔗 循环设备: $LOOP"

sudo mkfs.ext4 -F -U "$UUID_ROOT" "${LOOP}p1"
sudo mkdir -p /mnt/weclaw-img
sudo mount "${LOOP}p1" /mnt/weclaw-img

# 复制 rootfs 到映像
log "📋 复制 rootfs 到磁盘映像..."
sudo cp -a "$ROOTFS"/* /mnt/weclaw-img/
sudo mkdir -p /mnt/weclaw-img/{proc,sys,dev,run}

# ── 8. 修复 initramfs（核心修复！）──
log "🔧 修复 initramfs /init 脚本..."
INITRAMFS_PATH="/mnt/weclaw-img/boot/initramfs-$KERNEL_FLAVOR"
INIT_EXTRACT="/tmp/fix_initramfs"

sudo mkdir -p "$INIT_EXTRACT"
cd "$INIT_EXTRACT"
sudo zcat "$INITRAMFS_PATH" | sudo cpio -idm 2>/dev/null || {
  log "⚠️  直接解压失败，用 gzip..."
  sudo gzip -d -c "$INITRAMFS_PATH" 2>/dev/null | sudo cpio -idm 2>/dev/null
}

# 在 /init 中 mount 前插入 findfs
INIT_FILE="$INIT_EXTRACT/init"
if [ -f "$INIT_FILE" ]; then
  log "🔍 找到 /init，查找 mount 位置..."
  
  # 找到第一个 mount /sysroot 的行号
  MOUNT_LINE=$(grep -n 'mount.*sysroot' "$INIT_FILE" | head -1 | cut -d: -f1 || echo "")
  
  if [ -n "$MOUNT_LINE" ]; then
    log "🔧 在行 $MOUNT_LINE 前插入 findfs..."
    # 在 mount 前插入 findfs 解析 UUID
    sudo sed -i "${MOUNT_LINE}i\\
# 微侠修复：用 findfs 解析 UUID/LABEL 到设备节点\\
case \"\$KOPT_root\" in\\
UUID=*|LABEL=*|PARTUUID=*)\\
    KOPT_root=\$(findfs \"\$KOPT_root\" 2>/dev/null || echo \"\$KOPT_root\")\\
    ;;\\
esac" "$INIT_FILE"
    log "✅ findfs 已插入"
  else
    log "⚠️  未找到 mount sysroot，尝试在末尾添加 findfs..."
    echo "" | sudo tee -a "$INIT_FILE" > /dev/null
    echo "# 微侠修复：findfs" | sudo tee -a "$INIT_FILE" > /dev/null
    echo "case \"\$KOPT_root\" in" | sudo tee -a "$INIT_FILE" > /dev/null
    echo "UUID=*|LABEL=*|PARTUUID=*)" | sudo tee -a "$INIT_FILE" > /dev/null
    echo '    KOPT_root=$(findfs "$KOPT_root" 2>/dev/null || echo "$KOPT_root")' | sudo tee -a "$INIT_FILE" > /dev/null
    echo "    ;;" | sudo tee -a "$INIT_FILE" > /dev/null
    echo "esac" | sudo tee -a "$INIT_FILE" > /dev/null
  fi

  # 重打包 initramfs
  log "📦 重打包 initramfs..."
  sudo cp "$INITRAMFS_PATH" "${INITRAMFS_PATH}.bak"
  cd "$INIT_EXTRACT"
  sudo find . -print0 | sudo cpio -o -H newc | sudo gzip -9 > "$INITRAMFS_PATH"
  log "✅ initramfs 重打包完成: $(ls -lh "$INITRAMFS_PATH" | awk '{print $5}')"
else
  log "❌ 未找到 /init 文件！"
fi

# 清理
cd /
sudo rm -rf "$INIT_EXTRACT"

# ── 9. 安装 GRUB ──
log "📀 安装 GRUB..."
sudo grub-install --target=i386-pc --boot-directory=/mnt/weclaw-img/boot "$LOOP"

# grub.cfg
UUID_FS=$(sudo blkid -s UUID -o value "${LOOP}p1")
cat <<GRUB | sudo tee /mnt/weclaw-img/boot/grub/grub.cfg > /dev/null
set timeout=5
set default=0

menuentry "微侠桌面 OS (Alpine)" {
    linux /boot/vmlinuz-$KERNEL_FLAVOR root=UUID=$UUID_FS quiet console=tty0
    initrd /boot/initramfs-$KERNEL_FLAVOR
}

menuentry "微侠桌面 OS (调试模式)" {
    linux /boot/vmlinuz-$KERNEL_FLAVOR root=UUID=$UUID_FS console=ttyS0,115200 loglevel=7
    initrd /boot/initramfs-$KERNEL_FLAVOR
}
GRUB

log "✅ GRUB 安装完成 (UUID=$UUID_FS)"

# ── 10. 清理并卸载 ──
log "🧹 清理..."
sudo umount /mnt/weclaw-img
sudo losetup -d "$LOOP"
sudo rm -rf /mnt/weclaw-img

log "🎉 镜像构建完成: $(ls -lh "$IMG" | awk '{print $5}')"

# ── 11. QEMU 测试（无交互，5秒后退出）──
log "🧪 QEMU 测试启动..."
timeout 10 qemu-system-x86_64 \
  -drive file="$IMG",format=raw,if=ide \
  -m 2G \
  -nographic \
  -display none \
  -serial mon:stdio \
  -no-reboot \
  2>&1 | head -40 || true

log "✅ 测试完成"
echo "========================================"
echo "  微侠桌面 OS → $IMG"
echo "  QEMU测试: qemu-system-x86_64 -drive file=$IMG,format=raw,if=ide -m 2G -nographic"
echo "========================================"
