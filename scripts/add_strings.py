#!/usr/bin/env python3
# -*- coding: utf-8 -*-

new_strings = """
    <!-- 内容描述 -->
    <string name="desc_permission_icon">权限图标</string>
    <string name="desc_info">信息</string>
    <string name="desc_check">检查</string>
    <string name="desc_warning">警告</string>
    <string name="desc_photo_library">照片库</string>
    <string name="desc_video_library">视频库</string>
    <string name="desc_gif">GIF动画</string>
    <string name="desc_video_file">视频文件</string>
    <string name="desc_wallpaper">壁纸</string>
    <string name="desc_blur_on">开启模糊</string>
    <string name="desc_expand">展开</string>
    <string name="desc_edit">编辑</string>
    <string name="desc_delete_outline">删除轮廓</string>
"""

file_path = "E:/KEchengbiao/app/src/main/res/values/strings.xml"

try:
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 检查是否已存在这些字符串
    if 'desc_permission_icon' not in content:
        # 在</resources> 标签前插入新字符串
        insert_pos = content.rfind('</resources>')
        if insert_pos != -1:
            new_content = content[:insert_pos] + new_strings + '\n' + content[insert_pos:]
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(new_content)
            print('成功添加缺失的字符串资源')
        else:
            print('未找到</resources> 标签')
    else:
        print('字符串资源已存在')
except Exception as e:
    print(f'处理文件时出错: {e}')
