package org.eclipse.pde.internal.preferences;/* * (c) Copyright IBM Corp. 2000, 2001. * All Rights Reserved. */import org.eclipse.swt.widgets.*;import org.eclipse.swt.SWT;import org.eclipse.swt.layout.*;import org.eclipse.jface.preference.*;
public class StringFieldEditorWithSpace extends StringFieldEditor {

	/**
	 * Constructor for StringFieldEditorWithSpace
	 */
	protected StringFieldEditorWithSpace() {
		super();
	}

	/**
	 * Constructor for StringFieldEditorWithSpace
	 */
	public StringFieldEditorWithSpace(
		String arg0,
		String arg1,
		int arg2,
		Composite arg3) {
		super(arg0, arg1, arg2, arg3);
	}

	/**
	 * Constructor for StringFieldEditorWithSpace
	 */
	public StringFieldEditorWithSpace(String arg0, String arg1, Composite arg2) {
		super(arg0, arg1, arg2);
	}		public int getNumberOfControls() {		return 3;	}		protected void doFillIntoGrid(Composite parent, int numColumns) {		super.doFillIntoGrid(parent, numColumns-1);		getTextControl().setLayoutData(new GridData(GridData.FILL_HORIZONTAL));		new Label(parent, SWT.NULL);	}	protected void adjustForNumColumns(int numColumns) {		((GridData)getTextControl().getLayoutData()).horizontalSpan = numColumns - 2;	}
}
