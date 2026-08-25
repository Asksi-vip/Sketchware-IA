import os
import subprocess
import requests
import re

def get_git_commit_info():
    try:
        commit_author = subprocess.check_output(['git', 'log', '-1', '--pretty=format:%an']).decode('utf-8')
        commit_message = subprocess.check_output(['git', 'log', '-1', '--pretty=format:%s']).decode('utf-8')
        commit_hash = subprocess.check_output(['git', 'log', '-1', '--pretty=format:%H']).decode('utf-8')
        commit_hash_short = subprocess.check_output(['git', 'log', '-1', '--pretty=format:%h']).decode('utf-8')
    except Exception:
        commit_author = "Developer"
        commit_message = "New build update"
        commit_hash = "latest"
        commit_hash_short = "latest"
    return commit_author, commit_message, commit_hash, commit_hash_short

def escape_markdown_v2(text):
    escape_chars = r'_~`#+-=|{}.!'
    return re.sub(r'([%s])' % re.escape(escape_chars), r'\\\1', text)

def escape_parentheses(text):
    return re.sub(r'([()])', r'\\\1', text)

def main():
    bot_token = os.environ.get('BOT_TOKEN') or '8598821558:AAGuCWdMA4uryFynQTn1qfJYH0ZLM7JaJ9c'
    chat_id = os.environ.get('CHAT_ID') or '-1004345954573'

    commit_author, commit_message, commit_hash, commit_hash_short = get_git_commit_info()

    message = (
        f"A new commit has been pushed to the repository by *{commit_author}*.\n\n"
        f"*What has changed:*\n>{escape_parentheses(commit_message)}\n\n"
        f"Building APK now and sending it here upon completion...\n\n#{commit_hash_short}"
    )

    escaped_message = escape_markdown_v2(message)

    url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
    payload = {
        "chat_id": chat_id,
        "text": escaped_message,
        "parse_mode": "markdownv2",
        "disable_web_page_preview": True
    }

    try:
        response = requests.post(url, json=payload, timeout=30)
        if response.status_code != 200:
            print(f"Failed to send message: {response.status_code} {response.text}")
        else:
            print("Message sent successfully to Telegram.")
    except Exception as e:
        print(f"Error sending Telegram notification: {e}")

if __name__ == "__main__":
    main()
