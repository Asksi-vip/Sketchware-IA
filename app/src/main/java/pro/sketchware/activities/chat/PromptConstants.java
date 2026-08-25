package pro.sketchware.activities.chat;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public final class PromptConstants {
    private PromptConstants() {
    }

    public static final List<String> TRIPLE_TICK = Collections.unmodifiableList(Arrays.asList("```", "```"));
    public static final int MAX_DIRSTR_CHARS_TOTAL_BEGINNING = 20_000;
    public static final int MAX_DIRSTR_CHARS_TOTAL_TOOL = 20_000;
    public static final int MAX_DIRSTR_RESULTS_TOTAL_BEGINNING = 100;
    public static final int MAX_DIRSTR_RESULTS_TOTAL_TOOL = 100;
    public static final int MAX_FILE_CHARS_PAGE = 500_000;
    public static final int MAX_CHILDREN_URIS_PAGE = 500;
    public static final int MAX_TERMINAL_CHARS = 100_000;
    public static final int MAX_TERMINAL_INACTIVE_TIME = 8;
    public static final int MAX_TERMINAL_BG_COMMAND_TIME = 5;
    public static final int MAX_PREFIX_SUFFIX_CHARS = 20_000;
    public static final int DEFAULT_FILE_SIZE_LIMIT = 2_000_000;

    public static final String ORIGINAL = "<<<<<<< ORIGINAL";
    public static final String DIVIDER = "=======";
    public static final String FINAL = ">>>>>>> UPDATED";

    public static final String SEARCH_REPLACE_BLOCK_TEMPLATE =
            ORIGINAL + "\n"
                    + "// ... original code goes here\n"
                    + DIVIDER + "\n"
                    + "// ... final code goes here\n"
                    + FINAL + "\n\n"
                    + ORIGINAL + "\n"
                    + "// ... original code goes here\n"
                    + DIVIDER + "\n"
                    + "// ... final code goes here\n"
                    + FINAL;

    public static final String SEARCH_REPLACE_BLOCKS_TOOL_DESCRIPTION =
            "A string of SEARCH/REPLACE block(s) which will be applied to the given file.\n"
                    + "Your SEARCH/REPLACE blocks string must be formatted as follows:\n"
                    + SEARCH_REPLACE_BLOCK_TEMPLATE + "\n\n"
                    + "## Guidelines:\n\n"
                    + "1. You may output multiple search replace blocks if needed.\n\n"
                    + "2. The ORIGINAL code in each SEARCH/REPLACE block must EXACTLY match lines in the original file. Do not add or remove any whitespace or comments from the original code.\n\n"
                    + "3. Each ORIGINAL text must be large enough to uniquely identify the change. However, bias towards writing as little as possible.\n\n"
                    + "4. Each ORIGINAL text must be DISJOINT from all other ORIGINAL text.\n\n"
                    + "5. This field is a STRING (not an array).";

    public static final String SEARCH_REPLACE_GIVEN_DESCRIPTION_SYSTEM_MESSAGE =
            "You are a coding assistant that takes in a diff, and outputs SEARCH/REPLACE code blocks to implement the change(s) in the diff.\n"
                    + "The diff will be labeled `DIFF` and the original file will be labeled `ORIGINAL_FILE`.\n\n"
                    + "Format your SEARCH/REPLACE blocks as follows:\n"
                    + TRIPLE_TICK.get(0) + "\n"
                    + SEARCH_REPLACE_BLOCK_TEMPLATE + "\n"
                    + TRIPLE_TICK.get(1) + "\n\n"
                    + "1. Your SEARCH/REPLACE block(s) must implement the diff EXACTLY. Do NOT leave anything out.\n\n"
                    + "2. You are allowed to output multiple SEARCH/REPLACE blocks to implement the change.\n\n"
                    + "3. Assume any comments in the diff are PART OF THE CHANGE. Include them in the output.\n\n"
                    + "4. Your output should consist ONLY of SEARCH/REPLACE blocks. Do NOT output any text or explanations before or after this.\n\n"
                    + "5. The ORIGINAL code in each SEARCH/REPLACE block must EXACTLY match lines in the original file. Do not add or remove any whitespace, comments, or modifications from the original code.\n\n"
                    + "6. Each ORIGINAL text must be large enough to uniquely identify the change in the file. However, bias towards writing as little as possible.\n\n"
                    + "7. Each ORIGINAL text must be DISJOINT from all other ORIGINAL text.";

    public static final String REWRITE_CODE_SYSTEM_MESSAGE =
            "You are a coding assistant that re-writes an entire file to make a change. You are given the original file `ORIGINAL_FILE` and a change `CHANGE`.\n\n"
                    + "Directions:\n"
                    + "1. Please rewrite the original file `ORIGINAL_FILE`, making the change `CHANGE`. You must completely re-write the whole file.\n"
                    + "2. Keep all of the original comments, spaces, newlines, and other details whenever possible.\n"
                    + "3. ONLY output the full new file. Do not add any other explanations or text.\n";

    public static final String GIT_COMMIT_MESSAGE_SYSTEM_MESSAGE =
            ("You are an expert software engineer AI assistant responsible for writing clear and concise Git commit messages that summarize the **purpose** and **intent** of the change. Try to keep your commit messages to one sentence. If necessary, you can use two sentences.\n\n"
                    + "You always respond with:\n"
                    + "- The commit message wrapped in <output> tags\n"
                    + "- A brief explanation of the reasoning behind the message, wrapped in <reasoning> tags\n\n"
                    + "Example format:\n"
                    + "<output>Fix login bug and improve error handling</output>\n"
                    + "<reasoning>This commit updates the login handler to fix a redirect issue and improves frontend error messages for failed logins.</reasoning>\n\n"
                    + "Do not include anything else outside of these tags.\n"
                    + "Never include quotes, markdown, commentary, or explanations outside of <output> and <reasoning>.").trim();

    public static final QuickEditFimTags DEFAULT_QUICK_EDIT_FIM_TAGS =
            new QuickEditFimTags("ABOVE", "BELOW", "SELECTION");

    public static final class PrefixSuffix {
        public final String prefix;
        public final String suffix;

        public PrefixSuffix(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }
    }

    public static final class QuickEditFimTags {
        public final String preTag;
        public final String sufTag;
        public final String midTag;

        public QuickEditFimTags(String preTag, String sufTag, String midTag) {
            this.preTag = preTag;
            this.sufTag = sufTag;
            this.midTag = midTag;
        }
    }

    public static PrefixSuffix voidPrefixAndSuffix(String fullFileStr, int startLine, int endLine) {
        if (fullFileStr == null || fullFileStr.isEmpty()) {
            return new PrefixSuffix("", "");
        }
        String[] fullFileLines = fullFileStr.split("\n", -1);
        int totalLines = fullFileLines.length;

        // Sanitize and clamp line numbers to [1, totalLines]
        int s = Math.max(1, Math.min(startLine, totalLines));
        int e = Math.max(1, Math.min(endLine, totalLines));
        if (s > e) s = e;

        String prefix = "";
        int i = s - 1;
        while (i > 0) {
            String newLine = fullFileLines[i - 1];
            if (newLine.length() + 1 + prefix.length() <= MAX_PREFIX_SUFFIX_CHARS) {
                prefix = newLine + "\n" + prefix;
                i -= 1;
            } else {
                break;
            }
        }

        String suffix = "";
        int j = e - 1;
        while (j < totalLines - 1) {
            String newLine = fullFileLines[j + 1];
            if (newLine.length() + 1 + suffix.length() <= MAX_PREFIX_SUFFIX_CHARS) {
                suffix = suffix + "\n" + newLine;
                j += 1;
            } else {
                break;
            }
        }

        return new PrefixSuffix(prefix, suffix);
    }

    public static String reParsedToolXmlString(String toolName, Map<String, String> toolParams) {
        StringJoiner params = new StringJoiner("\n");
        for (Map.Entry<String, String> entry : toolParams.entrySet()) {
            params.add("<" + entry.getKey() + ">" + entry.getValue() + "</" + entry.getKey() + ">");
        }
        String paramsString = params.toString();
        return ("    <" + toolName + ">" + (paramsString.isEmpty() ? "" : "\n" + paramsString)
                + "\n    </" + toolName + ">").replace("\t", "  ");
    }

    public static String rewriteCodeUserMessage(String originalCode, String applyStr, String language) {
        return "ORIGINAL_FILE\n"
                + TRIPLE_TICK.get(0) + language + "\n"
                + originalCode + "\n"
                + TRIPLE_TICK.get(1) + "\n\n"
                + "CHANGE\n"
                + TRIPLE_TICK.get(0) + "\n"
                + applyStr + "\n"
                + TRIPLE_TICK.get(1) + "\n\n"
                + "INSTRUCTIONS\n"
                + "Please finish writing the new file by applying the change to the original file. Return ONLY the completion of the file, without any explanation.\n";
    }

    public static String searchReplaceGivenDescriptionUserMessage(String originalCode, String applyStr) {
        return "DIFF\n"
                + applyStr + "\n\n"
                + "ORIGINAL_FILE\n"
                + TRIPLE_TICK.get(0) + "\n"
                + originalCode + "\n"
                + TRIPLE_TICK.get(1);
    }

    public static String ctrlKStreamSystemMessage(QuickEditFimTags tags) {
        return "You are a FIM (fill-in-the-middle) coding assistant. Your task is to fill in the middle SELECTION marked by <" + tags.midTag + "> tags.\n\n"
                + "The user will give you INSTRUCTIONS, as well as code that comes BEFORE the SELECTION, indicated with <" + tags.preTag + ">...before</" + tags.preTag + ">, and code that comes AFTER the SELECTION, indicated with <" + tags.sufTag + ">...after</" + tags.sufTag + ">.\n"
                + "The user will also give you the existing original SELECTION that will be be replaced by the SELECTION that you output, for additional context.\n\n"
                + "Instructions:\n"
                + "1. Your OUTPUT should be a SINGLE PIECE OF CODE of the form <" + tags.midTag + ">...new_code</" + tags.midTag + ">. Do NOT output any text or explanations before or after this.\n"
                + "2. You may ONLY CHANGE the original SELECTION, and NOT the content in the <" + tags.preTag + ">...</" + tags.preTag + "> or <" + tags.sufTag + ">...</" + tags.sufTag + "> tags.\n"
                + "3. Make sure all brackets in the new selection are balanced the same as in the original selection.\n"
                + "4. Be careful not to duplicate or remove variables, comments, or other syntax by mistake.\n";
    }

    public static String ctrlKStreamUserMessage(String selection, String prefix, String suffix,
                                                String instructions, QuickEditFimTags fimTags, String language) {
        return "\nCURRENT SELECTION\n"
                + TRIPLE_TICK.get(0) + language + "\n"
                + "<" + fimTags.midTag + ">" + selection + "</" + fimTags.midTag + ">\n"
                + TRIPLE_TICK.get(1) + "\n\n"
                + "INSTRUCTIONS\n"
                + instructions + "\n\n"
                + "<" + fimTags.preTag + ">" + prefix + "</" + fimTags.preTag + ">\n"
                + "<" + fimTags.sufTag + ">" + suffix + "</" + fimTags.sufTag + ">\n\n"
                + "Return only the completion block of code (of the form " + TRIPLE_TICK.get(0) + language + "\n"
                + "<" + fimTags.midTag + ">...new code</" + fimTags.midTag + ">\n"
                + TRIPLE_TICK.get(1) + ").";
    }

    public static String gitCommitMessageUserMessage(String stat, String sampledDiffs, String branch, String log) {
        return ("Based on the following Git changes, write a clear, concise commit message that accurately summarizes the intent of the code changes.\n\n"
                + "Section 1 - Summary of Changes (git diff --stat):\n\n"
                + stat + "\n\n"
                + "Section 2 - Sampled File Diffs (Top changed files):\n\n"
                + sampledDiffs + "\n\n"
                + "Section 3 - Current Git Branch:\n\n"
                + branch + "\n\n"
                + "Section 4 - Last 5 Commits (excluding merges):\n\n"
                + log).trim();
    }

    public static String todayDateForPrompt() {
        return new SimpleDateFormat("EEE MMM d yyyy", Locale.US).format(new Date());
    }
}
