package org.eclipse.pde.internal.junit.runtime;

import junit.framework.*;

import org.eclipse.core.boot.IPlatformRunnable;
import org.eclipse.core.runtime.*;
import org.eclipse.ui.*;

/**
 * @author melhem
 *
 */
public class LegacyUITestApplication implements IPlatformRunnable {

	private static final String DEFAULT_APP_PRE_3_0 = "org.eclipse.ui.workbench";

	public Object run(final Object args) throws Exception {
		IPlatformRunnable object = getApplication((String[]) args);
		
		Assert.assertNotNull(object);
		Assert.assertTrue(object instanceof IWorkbench);

		final IWorkbench workbench = (IWorkbench) object;
		// the 'started' flag is used so that we only run tests when the window
		// is opened for the first time only.
		final boolean[] started = { false };
		workbench.addWindowListener(new IWindowListener() {
			public void windowOpened(IWorkbenchWindow w) {
				if (started[0])
					return;
				w.getShell().getDisplay().asyncExec(new Runnable() {
					public void run() {
						started[0] = true;
						RemotePluginTestRunner.main((String[]) args);
						workbench.close();
					}
				});
			}
			public void windowActivated(IWorkbenchWindow window) {
			}
			public void windowDeactivated(IWorkbenchWindow window) {
			}
			public void windowClosed(IWorkbenchWindow window) {
			}
		});
		return ((IPlatformRunnable) workbench).run(args);
	}
	
	
	private IPlatformRunnable getApplication(String[] args) throws CoreException {
		IExtension extension =
			Platform.getPluginRegistry().getExtension(
				Platform.PI_RUNTIME,
				Platform.PT_APPLICATIONS,
				DEFAULT_APP_PRE_3_0);

		Assert.assertNotNull(extension);

		// If the extension does not have the correct grammar, return null.
		// Otherwise, return the application object.
		IConfigurationElement[] elements = extension.getConfigurationElements();
		if (elements.length > 0) {
			IConfigurationElement[] runs = elements[0].getChildren("run");
			if (runs.length > 0) {
				Object runnable = runs[0].createExecutableExtension("class");
				if (runnable instanceof IPlatformRunnable)
					return (IPlatformRunnable) runnable;
			}
		}
		return null;
	}
}
