/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.wizards.tools;

import java.lang.reflect.*;
import java.util.*;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.operation.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.wizard.*;
import org.eclipse.pde.core.plugin.*;
import org.eclipse.pde.internal.*;
import org.eclipse.pde.internal.core.*;
import org.eclipse.pde.internal.core.plugin.*;
import org.eclipse.pde.internal.ui.*;
import org.eclipse.pde.internal.ui.elements.*;
import org.eclipse.pde.internal.ui.parts.*;
import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.actions.*;
import org.eclipse.ui.help.*;
import org.eclipse.ui.ide.*;
	
public class ConvertedProjectsPage extends WizardPage  {
	private CheckboxTableViewer projectViewer;
	public static final String KEY_TITLE = "ConvertedProjectWizard.title"; //$NON-NLS-1$
	public static final String KEY_CONVERTING = "ConvertedProjectWizard.converting"; //$NON-NLS-1$
	public static final String KEY_UPDATING = "ConvertedProjectWizard.updating"; //$NON-NLS-1$
	public static final String KEY_DESC = "ConvertedProjectWizard.desc"; //$NON-NLS-1$
	public static final String KEY_PROJECT_LIST =
		"ConvertedProjectWizard.projectList"; //$NON-NLS-1$
	private TablePart tablePart;
	private IProject[] fSelected;
	private IProject[] fUnconverted;
	
	public class ProjectContentProvider
		extends DefaultContentProvider
		implements IStructuredContentProvider {
		public Object[] getElements(Object parent) {	
			if (fUnconverted!= null)
				return fUnconverted;
			return new Object[0];
		}		
	}

	public class ProjectLabelProvider
		extends LabelProvider
		implements ITableLabelProvider {
		public String getColumnText(Object obj, int index) {
			if (index == 0) 
				return ((IProject) obj).getName();
			return ""; //$NON-NLS-1$
		}
		public Image getColumnImage(Object obj, int index) {
			return PlatformUI.getWorkbench().getSharedImages().getImage(IDE.SharedImages.IMG_OBJ_PROJECT);
		}
	}

	class TablePart extends WizardCheckboxTablePart {
		public TablePart(String mainLabel) {
			super(mainLabel);
		}
		public void updateCounter(int count) {
			super.updateCounter(count);
			setPageComplete(count > 0);
		}
	}

	public ConvertedProjectsPage(IProject[] projects, Vector initialSelection) {
		super("convertedProjects"); //$NON-NLS-1$
		setTitle(PDEPlugin.getResourceString(KEY_TITLE));
		setDescription(PDEPlugin.getResourceString(KEY_DESC));
		tablePart = new TablePart(PDEPlugin.getResourceString(KEY_PROJECT_LIST));
		this.fSelected = (IProject[])initialSelection.toArray(new IProject[initialSelection.size()]);
		this.fUnconverted = projects;
	}
	
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginHeight = 0;
		layout.marginWidth = 5;
		container.setLayout(layout);

		tablePart.createControl(container);

		projectViewer = tablePart.getTableViewer();
		projectViewer.setContentProvider(new ProjectContentProvider());
		projectViewer.setLabelProvider(new ProjectLabelProvider());
		projectViewer.setInput(PDEPlugin.getWorkspace());
	
		tablePart.setSelection(fSelected);
		tablePart.updateCounter(fSelected.length);

		setControl(container);
		Dialog.applyDialogFont(container);
		WorkbenchHelp.setHelp(container, IHelpContextIds.CONVERTED_PROJECTS);
	}


	private static String createInitialName(String id) {
		int loc = id.lastIndexOf('.');
		if (loc == -1)
			return id;
		StringBuffer buf = new StringBuffer(id.substring(loc + 1));
		buf.setCharAt(0, Character.toUpperCase(buf.charAt(0)));
		return buf.toString();
	}
	private static void createManifestFile(IFile file, IProgressMonitor monitor)
		throws CoreException {
		WorkspacePluginModel model = new WorkspacePluginModel(file);
		model.load();
		IPlugin plugin = model.getPlugin();
		if (PDECore.getDefault().getModelManager().isOSGiRuntime())
			plugin.setSchemaVersion("3.0"); //$NON-NLS-1$
		plugin.setId(file.getProject().getName());
		plugin.setName(createInitialName(plugin.getId()));
		plugin.setVersion("1.0.0"); //$NON-NLS-1$
		model.save();
	}
	
	public boolean finish() {
		final Object [] selected = tablePart.getSelection();
		
		IRunnableWithProgress operation = new WorkspaceModifyOperation() {
			public void execute(IProgressMonitor monitor) {
				try {
					convertProjects(selected, monitor);
				} catch (CoreException e) {
					PDEPlugin.logException(e);
				} finally {
					monitor.done();
				}
			}
		};
		try {
			getContainer().run(false, true, operation);
		} catch (InvocationTargetException e) {
			PDEPlugin.logException(e);
			return false;
		} catch (InterruptedException e) {
			PDEPlugin.logException(e);
			return false;
		}
		return true;
	}
	
	public static void updateBuildPath(IProject project, IProgressMonitor monitor)
		throws CoreException {
		IPath manifestPath = project.getFullPath().append("plugin.xml"); //$NON-NLS-1$
		IFile file = project.getWorkspace().getRoot().getFile(manifestPath);
		if (!file.exists())
			return;
		WorkspacePluginModel model = new WorkspacePluginModel(file);
		model.load();
		if (!model.isLoaded())
			return;
			
		ClasspathUtilCore.setClasspath(model, monitor); // error occurs here on initial
	
	}

	public static void convertProject(
		IProject project,
		IProgressMonitor monitor)
		throws CoreException {
		CoreUtility.addNatureToProject(project, PDE.PLUGIN_NATURE, monitor);
		IPath manifestPath = project.getFullPath().append("plugin.xml"); //$NON-NLS-1$
		IFile file = project.getWorkspace().getRoot().getFile(manifestPath);
		if (file.exists()) {
			IDE.setDefaultEditor(file, PDEPlugin.MANIFEST_EDITOR_ID);
		} else {
			manifestPath = project.getFullPath().append("fragment.xml"); //$NON-NLS-1$
			IFile fragmentFile = project.getWorkspace().getRoot().getFile(manifestPath);
			if (!fragmentFile.exists()) {
				createManifestFile(file, monitor);				
			}
			IDE.setDefaultEditor(file, PDEPlugin.MANIFEST_EDITOR_ID);
		}
		
		IPath buildPath = project.getFullPath().append("build.properties"); //$NON-NLS-1$
		IFile buildFile = project.getWorkspace().getRoot().getFile(buildPath);
		if (buildFile.exists()) {
			IDE.setDefaultEditor(buildFile, PDEPlugin.BUILD_EDITOR_ID);
		}
	}
	
	private void convertProjects(
		Object[] selected,
		IProgressMonitor monitor)
		throws CoreException {
		int totalCount = 2 * selected.length;
		monitor.beginTask(
			PDEPlugin.getResourceString(KEY_CONVERTING),
			totalCount);
		for (int i = 0; i < selected.length; i++) {
			convertProject((IProject) selected[i], monitor);
			monitor.worked(1);
		}
		
		// update build path
		monitor.subTask(PDEPlugin.getResourceString(KEY_UPDATING));
		for (int i = 0; i < selected.length; i++) {
			if (((IProject) selected[i]).hasNature(JavaCore.NATURE_ID)) {
				updateBuildPath((IProject) selected[i], new SubProgressMonitor(monitor,1));
			} else {
				monitor.worked(1);
			}
		}
		monitor.done();
	}

}
