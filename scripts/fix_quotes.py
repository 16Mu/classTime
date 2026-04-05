#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os

def replace_chinese_quotes(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    content = content.replace('"', '"').replace('"', '"')
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
    
    print(f'Replaced Chinese quotes in {file_path}')

if __name__ == '__main__':
    files = [
        r'E:\KEchengbiao\app\src\main\java\com\wind\ggbond\classtime\ui\components\ScheduleQuickEditDialog.kt',
        r'E:\KEchengbiao\app\src\main\java\com\wind\ggbond\classtime\ui\components\ScheduleSelectionDialog.kt',
        r'E:\KEchengbiao\app\src\main\java\com\wind\ggbond\classtime\ui\screen\course\CourseDetailScreen.kt'
    ]
    
    for file_path in files:
        if os.path.exists(file_path):
            replace_chinese_quotes(file_path)
        else:
            print(f'File not found: {file_path}')
