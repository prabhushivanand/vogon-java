/*
 * Vogon personal finance/expense analyzer.
 * License TBD.
 * Author: Dmitry Zolotukhin <zlogic@gmail.com>
 */
package org.zlogic.vogon.ui;

import java.awt.Color;
import java.awt.Component;
import java.text.MessageFormat;
import java.util.Currency;
import java.util.ResourceBundle;
import javax.swing.DefaultCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

/**
 * Helper class for rendering/editing finance amounts (with currency)
 *
 * @author Dmitry
 */
public class SumTableCell {

	private static final ResourceBundle messages = ResourceBundle.getBundle("org/zlogic/vogon/ui/messages");
	/**
	 * The balance/amount
	 */
	protected double balance;
	/**
	 * The currency
	 */
	protected Currency currency;
	/**
	 * The amount is OK (e.g. zero sum for a transfer transaction)
	 */
	protected boolean isOk;

	/**
	 * Constructs a SumTableCell
	 *
	 * @param balance the initial cell balance
	 * @param isOk if the cell data is OK (e.g. zero sum for a transfer
	 * transaction)
	 * @param currency the currency
	 */
	public SumTableCell(double balance, boolean isOk, Currency currency) {
		this.balance = balance;
		this.currency = currency;
		this.isOk = isOk;
	}

	/**
	 * Constructs a SumTableCell. Currency will be invalid.
	 *
	 * @param balance the initial cell balance
	 * @param isOk if the cell data is OK (e.g. zero sum for a transfer
	 * transaction)
	 */
	public SumTableCell(double balance, boolean isOk) {
		this.balance = balance;
		this.isOk = isOk;
	}

	/**
	 * Constructs a SumTableCell. Currency will be invalid, balance will be
	 * considered to be OK.
	 *
	 * @param balance the initial cell balance
	 */
	public SumTableCell(double balance) {
		this.balance = balance;
		this.isOk = true;
	}

	@Override
	public String toString() {
		String formattedSum = MessageFormat.format(messages.getString("FORMAT_SUM"), balance, currency != null ? currency.getCurrencyCode() : messages.getString("INVALID_CURRENCY"));
		return formattedSum;
	}

	/**
	 * Returns the customized cell renderer
	 *
	 * @return the customized cell renderer
	 */
	public static TableCellRenderer getRenderer() {
		SumModelRenderer renderer = new SumModelRenderer();
		renderer.setHorizontalAlignment(JLabel.RIGHT);
		return renderer;
	}

	/**
	 * Returns the customized cell editor
	 *
	 * @return the customized cell editor
	 */
	public static TableCellEditor getEditor() {
		JTextField textField = new JTextField();
		textField.setBorder(new LineBorder(Color.black));//Borrowed from JTable
		textField.setHorizontalAlignment(JLabel.RIGHT);
		return new SumModelEditor(textField);
	}

	/**
	 * The customized cell renderer class Renders cells where !isOK with a red
	 * background
	 */
	protected static class SumModelRenderer extends DefaultTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			if (value instanceof SumTableCell && !((SumTableCell) value).isOk)
				setBackground(Color.red);
			else
				setBackground(null);
			Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			return component;
		}
	}

	/**
	 * The customized cell editor class Calls the editor for double values
	 * instead of string
	 */
	protected static class SumModelEditor extends DefaultCellEditor {

		/**
		 * Constructs a
		 * <code>DefaultCellEditor</code> that uses a text field.
		 *
		 * @param textField a <code>JTextField</code> object
		 */
		public SumModelEditor(final JTextField textField) {
			super(textField);
		}

		/**
		 * Constructs a
		 * <code>DefaultCellEditor</code> object that uses a check box.
		 *
		 * @param checkBox a <code>JCheckBox</code> object
		 */
		public SumModelEditor(final JCheckBox checkBox) {
			super(checkBox);
		}

		/**
		 * Constructs a
		 * <code>DefaultCellEditor</code> object that uses a combo box.
		 *
		 * @param comboBox a <code>JComboBox</code> object
		 */
		public SumModelEditor(final JComboBox comboBox) {
			super(comboBox);
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
			return super.getTableCellEditorComponent(table, ((SumTableCell) value).balance, isSelected, row, column);
		}
	}
}