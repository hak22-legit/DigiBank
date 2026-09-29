package com.bank.console.screens;

import com.bank.controller.ReportController;
import com.bank.model.TransactionView;
import com.bank.model.dto.AccountDTO;
import com.bank.model.entity.User;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.util.List;
import java.util.Map;

/**
 * View helper and delegator for the Transaction Details modal.
 */
public final class TransactionDetailView {

    private TransactionDetailView() {}

    public static String render(TransactionView tv, AccountDTO account, User user,
                                Map<Long, String> categoryNames, int actionIdx,
                                String feedbackMsg, int width) {
        return TransactionDetailsScreen.renderContent(tv, account, user, categoryNames, actionIdx, feedbackMsg, width);
    }

    public static void show(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                            TransactionView tv, AccountDTO account, User user,
                            Map<Long, String> categoryNames, ReportController reportController, int width) {
        TransactionDetailsScreen.show(terminal, origAttr, reader, tv, account, user, categoryNames, reportController, width);
    }

    public static String formatExportPath(String rawPath, int maxLen) {
        return TransactionDetailsScreen.formatExportPath(rawPath, maxLen);
    }

    public static String formatAuditMemo(String rawMemo) {
        return TransactionDetailsScreen.formatAuditMemo(rawMemo);
    }

    public static List<String> wrapDescription(String text, int maxLen) {
        return TransactionDetailsScreen.wrapDescription(text, maxLen);
    }
}
