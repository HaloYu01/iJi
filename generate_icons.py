from PIL import Image
import os

# 源图片路径（放在 tools/ 而不是 res/：res 下的图会被打进 APK，这张 2.6MB 只是生成用的底图）
source_path = r"Y:\iji-android\tools\icon_source.png"

# 打开源图片
img = Image.open(source_path)
print(f"源图片尺寸: {img.size}")

# 不同密度的图标尺寸（前景图 108dp）
densities = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}

# 基础资源目录
res_dir = r"Y:\iji-android\app\src\main\res"

# 生成各尺寸的图标
for density, size in densities.items():
    mipmap_dir = os.path.join(res_dir, f"mipmap-{density}")
    os.makedirs(mipmap_dir, exist_ok=True)
    
    # 调整尺寸
    resized = img.resize((size, size), Image.Resampling.LANCZOS)
    
    # 保存前景图
    foreground_path = os.path.join(mipmap_dir, "ic_launcher_foreground.png")
    resized.save(foreground_path, "PNG")
    print(f"生成 {foreground_path} ({size}x{size})")
    
    # 同时保存 ic_launcher.png 和 ic_launcher_round.png
    launcher_path = os.path.join(mipmap_dir, "ic_launcher.png")
    resized.save(launcher_path, "PNG")
    
    round_path = os.path.join(mipmap_dir, "ic_launcher_round.png")
    resized.save(round_path, "PNG")

print("完成！")
