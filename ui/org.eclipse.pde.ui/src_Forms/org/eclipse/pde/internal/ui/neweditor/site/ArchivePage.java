/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.neweditor.site;

import org.eclipse.pde.internal.ui.neweditor.*;
import org.eclipse.swt.layout.*;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.widgets.*;

/**
 * 
 * Features page.
 */
public class ArchivePage extends PDEFormPage {
	public static final String PAGE_ID = "archives";
	private DescriptionSection fDescSection;
	private ArchiveSection fArchiveSection;
	
	public ArchivePage(PDEFormEditor editor) {
		super(editor, PAGE_ID, "Site Layout");
		//TODO translate page title
	}
	protected void createFormContent(IManagedForm mform) {
		ScrolledForm form = mform.getForm();
		GridLayout layout = new GridLayout();
		layout.verticalSpacing = 10;
		form.getBody().setLayout(layout);
		
		fDescSection = new DescriptionSection(this, form.getBody());
		fDescSection.getSection().setLayoutData(new GridData(GridData.FILL_BOTH));
		
		fArchiveSection = new ArchiveSection(this, form.getBody());
		fArchiveSection.getSection().setLayoutData(new GridData(GridData.FILL_BOTH));
		
		mform.addPart(fDescSection);
		mform.addPart(fArchiveSection);
		
		//WorkbenchHelp.setHelp(form.getBody(),
		// IHelpContextIds.MANIFEST_SITE_OVERVIEW);
		//TODO translate page header
		form.setText("Description and Layout");
	}
}