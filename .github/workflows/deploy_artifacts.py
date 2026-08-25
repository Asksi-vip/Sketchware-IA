import os
import subprocess
import requests

def get_git_commit_info():
    try:
        commit_author = subprocess.check_output(['git', 'log', '-1', '--pretty=format:%an']).decode('utf-8')
        commit_message = subprocess.check_output(['git', 'log', '-1', '--pretty=format:%s']).decode('utf-8')
        commit_hash_short = subprocess.check_output(['git', 'log', '-1', '--pretty=format:%h']).decode('utf-8')
    except Exception:
        commit_author = "المطور"
        commit_message = "تحديث جديد"
        commit_hash_short = "latest"
    return commit_author, commit_message, commit_hash_short

def human_readable_size(size, decimal_places=2):
    for unit in ['B', 'KB', 'MB', 'GB', 'TB']:
        if size < 1024.0:
            break
        size /= 1024.0
    return f"{size:.{decimal_places}f} {unit}"

def main():
    bot_token = os.environ.get("BOT_TOKEN") or "8598821558:AAGuCWdMA4uryFynQTn1qfJYH0ZLM7JaJ9c"
    chat_id = os.environ.get("CHAT_ID") or "-1004345954573"
    apk_path = os.environ.get("APK_PATH")

    if not apk_path or not os.path.exists(apk_path):
        print("File not found:", apk_path)
        return

    commit_author, commit_message, commit_hash_short = get_git_commit_info()
    file_size = os.path.getsize(apk_path)
    size_str = human_readable_size(file_size)

    caption = (
        f"✅ *تم بنجاح بناء تطبيق Sketchware IA!*\n\n"
        f"👤 *المطور:* {commit_author}\n"
        f"📝 *التغييرات:* {commit_message}\n"
        f"📌 *معرف التحديث:* #{commit_hash_short}\n"
        f"📦 *حجم الملف:* {size_str}\n\n"
        f"📱 يمكنك تنزيل وتثبيت الـ APK الآن على هاتفك."
    )

    url = f"https://api.telegram.org/bot{bot_token}/sendDocument"
    print(f"Uploading APK ({size_str}) to Telegram chat {chat_id}...")

    with open(apk_path, 'rb') as f:
        files = {'document': f}
        data = {
            'chat_id': chat_id,
            'caption': caption,
            'parse_mode': 'markdown'
        }
        response = requests.post(url, data=data, files=files, timeout=300)

    if response.status_code == 200:
        print("APK file sent successfully to Telegram!")
    else:
        print(f"Failed to send APK: {response.status_code} {response.text}")

if __name__ == "__main__":
    main()
