/*******************************************************************************
 * Copyright (c) 2003, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.preferences;

import org.eclipse.core.runtime.*;
import org.eclipse.jface.preference.*;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.swt.graphics.Image;

public class BuildOptionsPreferenceNode implements IPreferenceNode {

	private BuildOptionsPreferencePage page;
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.IPreferenceNode#add(org.eclipse.jface.preference.IPreferenceNode)
	 */
	public void add(IPreferenceNode node) {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.IPreferenceNode#createPage()
	 */
	public void createPage() {
		page = new BuildOptionsPreferencePage();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.IPreferenceNode#disposeResources()
	 */
	public void disposeResources() {
		if (page!=null) page.dispose();
		page = null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.IPreferenceNode#findSubNode(java.lang.String)
	 */
	public IPreferenceNode findSubNode(String id) {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.IPreferenceNode#getId()
	 */
	public String getId() {
		return "org.eclipse.pde.ui.buildOptionsPreferencePage"; //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.IPreferenceNode#getLabelImage()
	 */
	public Image getLabelImage() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.IPreferenceNode#getLabelText()
	 */
	public String getLabelText() {
		return Platform.getResourceString(PDEPlugin.getDefault().getBundle(), "%preferences.buildOptions.name"); //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.IPreferenceNode#getPage()
	 */
	public IPreferencePage getPage() {
		return page;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.IPreferenceNode#getSubNodes()
	 */
	public IPreferenceNode[] getSubNodes() {
		return new IPreferenceNode[0];
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.IPreferenceNode#remove(java.lang.String)
	 */
	public IPreferenceNode remove(String id) {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.IPreferenceNode#remove(org.eclipse.jface.preference.IPreferenceNode)
	 */
	public boolean remove(IPreferenceNode node) {
		return false;
	}

}
