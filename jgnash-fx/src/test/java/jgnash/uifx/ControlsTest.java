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
package jgnash.uifx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import jgnash.engine.Account;
import jgnash.engine.AccountType;
import jgnash.engine.CurrencyNode;
import jgnash.engine.DataStoreType;
import jgnash.engine.DefaultCurrencies;
import jgnash.engine.Engine;
import jgnash.engine.EngineFactory;
import jgnash.uifx.control.AccountComboBox;
import jgnash.uifx.control.DatePickerEx;
import jgnash.uifx.control.DecimalTextField;
import jgnash.uifx.control.TransactionNumberComboBox;
import jgnash.uifx.utils.StageUtils;

import com.sun.javafx.css.StyleManager;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Craig Cavanaugh
 */
public class ControlsTest extends Application {

    private String testFile;

    private String tempFile;

    //private Engine engine;

    private final boolean oldExportState = EngineFactory.exportXMLOnClose();

    static final char[] PASSWORD = new char[]{};

    public static void main(final String[] args) {
        //Locale.setDefault(Locale.FRANCE);


        launch(args);
    }

    @Override
    public void start(final Stage primaryStage) throws Exception {

        Engine engine = createEngine();
        //Objects.requireNonNull(engine);

        // Force application wide style sheet. Use is StyleManager is a private API and may break later
        Application.setUserAgentStylesheet(null);
        StyleManager.getInstance().addUserAgentStylesheet(MainApplication.DEFAULT_CSS);

        DecimalTextField decimalTextField = new DecimalTextField();
        DecimalTextField decimalTextField2 = new DecimalTextField();
        decimalTextField2.setScale(4);

        primaryStage.setTitle("Controls Test");
        Button btn = new Button();

        btn.setText("getDecimal()");
        btn.setOnAction(event -> System.out.println(decimalTextField.getDecimal()));

        // Create the DatePicker.
        DatePicker datePicker = new DatePickerEx();

        datePicker.setOnAction(event -> {
            LocalDate date = datePicker.getValue();
            System.out.println("Selected date: " + date);
        });

        TransactionNumberComboBox numberComboBox = new TransactionNumberComboBox();
        numberComboBox.setAccount(engine.getAccountList().get(0));

        VBox vBox = new VBox();
        vBox.getChildren().addAll(decimalTextField, decimalTextField2, datePicker, new AccountComboBox(), numberComboBox, btn);

        primaryStage.setScene(new Scene(vBox, 300, 250));

        StageUtils.applyDialogFormCSS(primaryStage);

        primaryStage.show();
    }

    @Override
    public void stop() throws IOException {
        EngineFactory.closeEngine(EngineFactory.DEFAULT);
        EngineFactory.setExportXMLOnClose(oldExportState);

        Files.deleteIfExists(Paths.get(testFile));

        cleanup();
    }

    private Engine createEngine() throws Exception {
        try {
            testFile = Files.createTempFile("test", "." + DataStoreType.BINARY_XSTREAM.getDataStore().getFileExt()).toFile().getAbsolutePath();
            tempFile = testFile;
        } catch (IOException e1) {
            Logger.getLogger(ControlsTest.class.getName()).log(Level.SEVERE, e1.getLocalizedMessage(), e1);
        }

        EngineFactory.deleteDatabase(testFile);

        Engine engine = EngineFactory.bootLocalEngine(testFile, EngineFactory.DEFAULT, PASSWORD, DataStoreType.BINARY_XSTREAM);

        Objects.requireNonNull(engine);

        CurrencyNode node = engine.getDefaultCurrency();

        if (!node.getSymbol().equals("USD")) {
            CurrencyNode defaultCurrency = DefaultCurrencies.buildNode(Locale.US);

            assertNotNull(defaultCurrency);
            assertTrue(engine.addCurrency(defaultCurrency));
            engine.setDefaultCurrency(defaultCurrency);
        }

        node = engine.getCurrency("CAD");

        if (node == null) {
            node = DefaultCurrencies.buildNode(Locale.CANADA);
            assertNotNull(node);
            assertTrue(engine.addCurrency(node));
        }

        Account account = new Account(AccountType.BANK, engine.getDefaultCurrency());
        account.setName("Bank Accounts");

        engine.addAccount(engine.getRootAccount(), account);

        return engine;
    }

    private void cleanup() throws IOException {
        Files.deleteIfExists(Paths.get(tempFile));
        Files.deleteIfExists(Paths.get(tempFile + ".backup"));
    }
}
