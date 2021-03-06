/*
 * jGnash, a personal finance application
 * Copyright (C) 2001-2014 Craig Cavanaugh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jgnash.ui.register.invest;

import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.JCheckBox;
import javax.swing.JTextField;

import jgnash.engine.AbstractInvestmentTransactionEntry;
import jgnash.engine.Account;
import jgnash.engine.InvestmentTransaction;
import jgnash.engine.ReconciledState;
import jgnash.engine.Transaction;
import jgnash.engine.TransactionEntry;
import jgnash.engine.TransactionEntryDividendX;
import jgnash.engine.TransactionFactory;
import jgnash.engine.TransactionTag;
import jgnash.ui.components.AccountSecurityComboBox;
import jgnash.ui.components.AutoCompleteFactory;
import jgnash.ui.components.DatePanel;
import jgnash.ui.components.JFloatField;
import jgnash.ui.register.AccountExchangePanel;
import jgnash.ui.util.ValidationFactory;

/**
 * Form for dividends.
 * 
 * @author Craig Cavanaugh
 *
 */
public final class DividendPanel extends AbstractInvTransactionPanel {

    private final DatePanel datePanel;

    private final JTextField memoField;

    private final JFloatField dividendField;

    private final AccountSecurityComboBox securityCombo;

    private final AccountExchangePanel accountExchangePanel;

    private final AccountExchangePanel incomeExchangePanel;

    private static final Logger logger = Logger.getLogger(DividendPanel.class.getName());

    DividendPanel(Account account) {
        super(account);

        datePanel = new DatePanel();
        memoField = AutoCompleteFactory.getMemoField();

        dividendField = new JFloatField(account.getCurrencyNode());

        securityCombo = new AccountSecurityComboBox(account);

        reconciledButton = new JCheckBox(rb.getString("Button.Reconciled"));

        incomeExchangePanel = new AccountExchangePanel(getAccount().getCurrencyNode(), null, dividendField);

        accountExchangePanel = new AccountExchangePanel(getAccount().getCurrencyNode(), null, dividendField);

        datePanel.getDateField().addKeyListener(keyListener);
        memoField.addKeyListener(keyListener);
        securityCombo.addKeyListener(keyListener);
        reconciledButton.addKeyListener(keyListener);

        layoutPanel();

        clearForm();
    }

    private void layoutPanel() {
        removeAll();

        FormLayout layout = new FormLayout("right:d, $lcgap, 50dlu:g, 8dlu, right:d, $lcgap, max(65dlu;min)", "f:d, $nlgap, f:d, $nlgap, f:d, $nlgap, f:d");

        layout.setRowGroups(new int[][] { { 1, 3, 5, 7 } });
        CellConstraints cc = new CellConstraints();

        setLayout(layout);

        add("Label.Security", cc.xy(1, 1));
        add(ValidationFactory.wrap(securityCombo), cc.xy(3, 1));
        add("Label.Date", cc.xy(5, 1));
        add(datePanel, cc.xy(7, 1));

        add("Label.Memo", cc.xy(1, 3));
        add(memoField, cc.xy(3, 3));
        add("Label.Dividend", cc.xy(5, 3));
        add(ValidationFactory.wrap(dividendField), cc.xy(7, 3));

        add("Label.IncomeAccount", cc.xy(1, 5));
        add(incomeExchangePanel, cc.xy(3, 5));
        add(reconciledButton, cc.xyw(5, 5, 3));

        add("Label.Account", cc.xy(1, 7));
        add(accountExchangePanel, cc.xy(3, 7));
    }

    @Override
    public void modifyTransaction(Transaction tran) {
        if (!(tran instanceof InvestmentTransaction)) {
            throw new IllegalArgumentException("bad tranType");
        }
        clearForm();

        datePanel.setDate(tran.getDate());

        List<TransactionEntry> entries = tran.getTransactionEntries();

        assert entries.size() <= 2;

        for (TransactionEntry e : entries) {
            if (e instanceof TransactionEntryDividendX) {
                AbstractInvestmentTransactionEntry entry = (AbstractInvestmentTransactionEntry) e;

                memoField.setText(e.getMemo());
                securityCombo.setSelectedNode(entry.getSecurityNode());

                incomeExchangePanel.setSelectedAccount(entry.getDebitAccount());
                incomeExchangePanel.setExchangedAmount(entry.getDebitAmount().abs());

                dividendField.setDecimal(entry.getAmount(getAccount()));
            } else if (e.getTransactionTag() == TransactionTag.INVESTMENT_CASH_TRANSFER) {
                accountExchangePanel.setSelectedAccount(e.getCreditAccount());
                accountExchangePanel.setExchangedAmount(e.getCreditAmount());
            } else {
                logger.warning("Invalid transaction");
            }
        }

        modTrans = tran;

        reconciledButton.setSelected(tran.getReconciled(getAccount()) != ReconciledState.NOT_RECONCILED);
    }

    @Override
    public Transaction buildTransaction() {
        BigDecimal incomeExchangedAmount = dividendField.getDecimal().negate();

        BigDecimal accountExchangedAmount = dividendField.getDecimal();

        if (!incomeExchangePanel.getSelectedAccount().getCurrencyNode().equals(getAccount().getCurrencyNode())) {
            incomeExchangedAmount = incomeExchangePanel.getExchangedAmount().negate();
        }

        if (!accountExchangePanel.getSelectedAccount().getCurrencyNode().equals(getAccount().getCurrencyNode())) {
            accountExchangedAmount = accountExchangePanel.getExchangedAmount();
        }

        return TransactionFactory.generateDividendXTransaction(incomeExchangePanel.getSelectedAccount(), getAccount(), accountExchangePanel.getSelectedAccount(), securityCombo.getSelectedNode(), dividendField.getDecimal(), incomeExchangedAmount, accountExchangedAmount, datePanel.getDate(), memoField.getText());
    }

    @Override
    public void clearForm() {
        modTrans = null;

        if (!getRememberLastDate()) {
            datePanel.setDate(new Date());
        }

        memoField.setText(null);

        reconciledButton.setSelected(false);

        accountExchangePanel.setSelectedAccount(getAccount());
        incomeExchangePanel.setSelectedAccount(getAccount());
    }

    @Override
    public boolean validateForm() {
        if (securityCombo.getSelectedNode() == null) {
            logger.warning(rb.getString("Message.Error.SecuritySelection"));
            showValidationError(rb.getString("Message.Error.SecuritySelection"), securityCombo);
            return false;
        }

        if (dividendField.isEmpty()) {
            logger.warning(rb.getString("Message.Error.DividendValue"));
            showValidationError(rb.getString("Message.Error.DividendValue"), dividendField);
            return false;
        }

        return true;
    }
}
