/*******************************************************************************
 *  Copyright (c) 2006, 2015 IBM Corporation and others.
 *
 *  This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License 2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-2.0/
 *
 *  SPDX-License-Identifier: EPL-2.0
 *
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.commands;

import java.util.ArrayList;
import java.util.TreeMap;

import org.eclipse.core.commands.Category;
import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.ui.commands.ICommandService;

public class CommandTreeContentProvider implements ITreeContentProvider {

	protected static final int F_CAT_CONTENT = 0; // category grouped content
	protected static final int F_CON_CONTENT = 1; // context grouped content

	private final ICommandService fComServ;
	private TreeMap<Category, ArrayList<Command>> fCatMap; // mapping of commands to category
	private Viewer fViewer;
	private int fCurContent = F_CAT_CONTENT;

	public CommandTreeContentProvider(ICommandService comServ) {
		fComServ = comServ;
		init();
	}

	private void init() {
		fCatMap = new TreeMap<>((arg0, arg1) -> {
			String comA = CommandList.getText(arg0);
			String comB = CommandList.getText(arg1);
			if (comA != null)
				return comA.compareTo(comB);
			return +1; // undefined ids should go last
		});
		Command[] commands = fComServ.getDefinedCommands();
		for (Command command : commands) {
			/*
			 * IWorkbenchRegistryConstants.AUTOGENERATED_PREFIX = "AUTOGEN:::"
			 */
			// skip commands with autogenerated id's
			if (command.getId().startsWith("AUTOGEN:::")) //$NON-NLS-1$
				continue;
			// populate category map
			try {
				Category cat = command.getCategory();
				ArrayList<Command> list = fCatMap.get(cat);
				if (list == null)
					fCatMap.put(cat, list = new ArrayList<>());
				list.add(command);
			} catch (NotDefinedException e) {
				continue;
			}
			// TODO: populate context map
			// can we easily group commands by context?
		}
	}

	@Override
	public Object getParent(Object element) {
		if (element instanceof Command)
			try {
				return ((Command) element).getCategory();
			} catch (NotDefinedException e) {
				// undefined category - should never hit this as these commands
				// will not be listed
			}
		return null;
	}

	@Override
	public void dispose() {
		fCatMap.clear();
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		if (parentElement instanceof Category) {
			ArrayList<Command> list = fCatMap.get(parentElement);
			if (list != null)
				return list.toArray(new Command[list.size()]);
		}
		return null;
	}

	@Override
	public boolean hasChildren(Object element) {
		if (element instanceof Category) {
			ArrayList<Command> list = fCatMap.get(element);
			if (list != null)
				return !list.isEmpty();
		}
		return false;
	}

	@Override
	public Object[] getElements(Object inputElement) {
		return switch (fCurContent) {
			case F_CAT_CONTENT -> fCatMap.keySet().toArray();
			case F_CON_CONTENT -> new Object[0];
			default -> null;
		};
	}

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		fViewer = viewer;
	}

	public void refreshWithCategoryContent() {
		fCurContent = F_CAT_CONTENT;
		if (fViewer != null)
			fViewer.refresh();
	}

	public void refreshWithContextContent() {
		fCurContent = F_CON_CONTENT;
		if (fViewer != null)
			fViewer.refresh();
	}

}