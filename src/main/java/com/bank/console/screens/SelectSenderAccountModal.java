package com.bank.console.screens;

import com.bank.model.dto.AccountDTO;
import com.bank.model.entity.Account;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.util.List;

/**
 * Dedicated Interactive Selector Modal for sender / source accounts in TransferScreen.
 * Strict 82-column layout conforming to DigiBank money movement specifications.
 */
public class SelectSenderAccountModal {

    public static final String TITLE = "DIGIBANK CORE > MONEY MOVEMENT > SELECT SENDER ACCOUNT";
    public static final String COMPARTMENT_HEADER = "AVAILABLE SOURCE ACCOUNTS";
    public static final String BALANCE_LABEL = "Bal: ";
    public static final String TIP_TEXT = "Tip: Select an account with sufficient balance for this transfer.";
    public static final String FOOTER_FORMAT = " [↑/↓] Navigate  •  [Enter] Confirm Selection  •  [%s] Hotkey  •  [Esc] Cancel";

    public static Account promptSelection(List<Account> accounts, String modalTitle) throws Exception {
        return AccountSelectorModal.promptSelection(accounts, modalTitle != null ? modalTitle : TITLE);
    }

    public static Account promptSelection(List<Account> accounts) throws Exception {
        return AccountSelectorModal.promptSelection(accounts, TITLE);
    }

    public static AccountDTO selectAccount(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                           List<AccountDTO> accounts, AccountDTO currentSelected, int width) {
        return AccountSelectorModal.showModal(
                terminal, origAttr, reader, accounts, currentSelected, width,
                TITLE, COMPARTMENT_HEADER, BALANCE_LABEL, TIP_TEXT, FOOTER_FORMAT
        );
    }

    public static AccountDTO selectAccount(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                           List<AccountDTO> accounts, AccountDTO currentSelected) {
        return selectAccount(terminal, origAttr, reader, accounts, currentSelected, 82);
    }

    public static String renderContent(List<AccountDTO> accounts, int selectedIdx, int width) {
        return AccountSelectorModal.renderModalContent(
                accounts, selectedIdx, width,
                TITLE, COMPARTMENT_HEADER, BALANCE_LABEL, TIP_TEXT, FOOTER_FORMAT
        );
    }

    public static String renderModal(List<Account> accounts, int selectedIndex, String modalTitle) {
        return AccountSelectorModal.renderModal(accounts, selectedIndex, modalTitle != null ? modalTitle : TITLE);
    }
}
