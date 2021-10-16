/*******************************************************************************
 * Copyright (c) 2007, 2022 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     EclipseSource Corporation - ongoing enhancements
 *     Hannes Wellmann - Bug 576885 - Unify methods to parse bundle-sets from launch-configs
 *     Hannes Wellmann - Bug 577118 - Handle multiple Plug-in versions in launching facility
 *     Hannes Wellmann - Bug 576886 - Clean up and improve BundleLaunchHelper
 *******************************************************************************/
package org.eclipse.pde.internal.launching.launcher;

import static java.util.Collections.emptySet;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.pde.core.plugin.*;
import org.eclipse.pde.internal.build.IPDEBuildConstants;
import org.eclipse.pde.internal.core.*;
import org.eclipse.pde.internal.core.ifeature.*;
import org.eclipse.pde.internal.core.util.VersionUtil;
import org.eclipse.pde.internal.launching.IPDEConstants;
import org.eclipse.pde.launching.IPDELauncherConstants;
import org.osgi.framework.Version;

public class BundleLauncherHelper {

	private BundleLauncherHelper() { // static use only
	}

	/**
	 * When creating a mapping of bundles to their start levels, update configurator is set
	 * to auto start at level three.  However, if at launch time we are launching with both
	 * simple configurator and update configurator, we change the start level as they
	 * shouldn't be started together.
	 */
	public static final String DEFAULT_UPDATE_CONFIGURATOR_START_LEVEL_TEXT = "3"; //$NON-NLS-1$
	public static final String DEFAULT_UPDATE_CONFIGURATOR_AUTO_START_TEXT = "true"; //$NON-NLS-1$
	public static final String DEFAULT_UPDATE_CONFIGURATOR_START_LEVEL = DEFAULT_UPDATE_CONFIGURATOR_START_LEVEL_TEXT + ":" + DEFAULT_UPDATE_CONFIGURATOR_AUTO_START_TEXT; //$NON-NLS-1$

	public static final char VERSION_SEPARATOR = '*';

	public static Map<IPluginModelBase, String> getWorkspaceBundleMap(ILaunchConfiguration configuration) throws CoreException {
		return getWorkspaceBundleMap(configuration, new HashMap<>());
	}

	public static Map<IPluginModelBase, String> getMergedBundleMap(ILaunchConfiguration configuration, boolean osgi) throws CoreException {

		ILaunchConfigurationWorkingCopy wc = getWorkingCopy(configuration);
		if (!osgi) {

			migrateLaunchConfiguration(wc);

			if (wc.getAttribute(IPDELauncherConstants.USE_DEFAULT, true)) {
				Map<IPluginModelBase, String> map = new LinkedHashMap<>();
				IPluginModelBase[] models = PluginRegistry.getActiveModels();
				for (IPluginModelBase model : models) {
					addBundleToMap(map, model, "default:default"); //$NON-NLS-1$
				}
				return map;
			}

		} else {
			migrateOsgiLaunchConfiguration(wc);
		}

		if (wc.getAttribute(IPDELauncherConstants.USE_CUSTOM_FEATURES, false)) {
			return getMergedBundleMapFeatureBased(wc, osgi);
		}

		return getAllSelectedPluginBundles(wc);
	}

	public static Map<IPluginModelBase, String> getAllSelectedPluginBundles(ILaunchConfiguration config) throws CoreException {
		Map<String, List<Version>> idVersions = new HashMap<>();
		Map<IPluginModelBase, String> map = getWorkspaceBundleMap(config, idVersions);
		map.putAll(getTargetBundleMap(config, idVersions));
		return map;
	}

	// --- feature based launches ---

	private static Map<IPluginModelBase, String> getMergedBundleMapFeatureBased(ILaunchConfiguration configuration, boolean osgi) throws CoreException {

		String defaultPluginResolution = configuration.getAttribute(IPDELauncherConstants.FEATURE_PLUGIN_RESOLUTION, IPDELauncherConstants.LOCATION_WORKSPACE);

		Map<IFeature, String> feature2resolution = getSelectedFeatures(configuration);

		// Get the feature model for each selected feature id and resolve its plugins
		Set<IPluginModelBase> launchPlugins = new HashSet<>();

		feature2resolution.forEach((feature, pluginResolution) -> {
			if (IPDELauncherConstants.LOCATION_DEFAULT.equalsIgnoreCase(pluginResolution)) {
				pluginResolution = defaultPluginResolution;
			}
			IFeaturePlugin[] featurePlugins = feature.getPlugins();
			for (IFeaturePlugin featurePlugin : featurePlugins) {
				IPluginModelBase plugin = getPlugin(featurePlugin.getId(), featurePlugin.getVersion(), pluginResolution);
				if (plugin != null) {
					launchPlugins.add(plugin);
				}
			}
			IFeatureImport[] featureImports = feature.getImports();
			for (IFeatureImport featureImport : featureImports) {
				if (featureImport.getType() == IFeatureImport.PLUGIN) {
					IPluginModelBase plugin = getPlugin(featureImport.getId(), featureImport.getVersion(), pluginResolution);
					if (plugin != null) {
						launchPlugins.add(plugin);
					}
				}
			}
		});

		Map<IPluginModelBase, AdditionalPluginData> additionalPlugins = getAdditionalPlugins(configuration, true);
		launchPlugins.addAll(additionalPlugins.keySet());

		// Get any plug-ins required by the application/product set on the config
		if (!osgi) {
			String[] applicationIds = RequirementHelper.getApplicationRequirements(configuration);
			for (String applicationId : applicationIds) {
				IPluginModelBase plugin = getPlugin(applicationId, null, defaultPluginResolution);
				if (plugin != null) {
					launchPlugins.add(plugin);
				}
			}
		}
		// Get all required plugins
		Set<BundleDescription> additionalBundles = DependencyManager.getDependencies(launchPlugins, false);
		for (BundleDescription bundle : additionalBundles) {
			IPluginModelBase plugin = getPlugin(bundle.getSymbolicName(), bundle.getVersion().toString(), defaultPluginResolution);
			launchPlugins.add(Objects.requireNonNull(plugin));// should never be null
		}

		// Create the start levels for the selected plugins and add them to the map
		Map<IPluginModelBase, String> map = new LinkedHashMap<>();
		for (IPluginModelBase model : launchPlugins) {
			AdditionalPluginData additionalPluginData = additionalPlugins.get(model);
			String startLevels = additionalPluginData != null ? additionalPluginData.startLevels() : "default:default"; //$NON-NLS-1$
			addBundleToMap(map, model, startLevels); // might override data of plug-ins included by feature
		}
		return map;
	}

	private static Map<IFeature, String> getSelectedFeatures(ILaunchConfiguration configuration) throws CoreException {
		String featureLocation = configuration.getAttribute(IPDELauncherConstants.FEATURE_DEFAULT_LOCATION, IPDELauncherConstants.LOCATION_WORKSPACE);

		// Get all available features
		Map<String, List<IFeature>> featureMaps = getPrioritizedAvailableFeatures(featureLocation);

		Set<String> selectedFeatures = configuration.getAttribute(IPDELauncherConstants.SELECTED_FEATURES, emptySet());

		Map<IFeature, String> feature2pluginResolution = new HashMap<>();
		for (String currentSelected : selectedFeatures) {
			String[] attributes = currentSelected.split(":"); //$NON-NLS-1$
			if (attributes.length > 1) {
				String id = attributes[0];
				String pluginResolution = attributes[1];
				IFeature feature = getFeature(id, featureMaps);
				if (feature != null) {
					feature2pluginResolution.put(feature, pluginResolution);
				}
			}
		}
		return feature2pluginResolution;
	}

	private static Map<String, List<IFeature>> getPrioritizedAvailableFeatures(String featureLocation) {
		FeatureModelManager fmm = PDECore.getDefault().getFeatureModelManager();
		List<IFeatureModel[]> featureModelsPerLocation = isWorkspace(featureLocation) //
				? List.of(fmm.getWorkspaceModels(), fmm.getExternalModels()) //
				: Collections.singletonList(fmm.getExternalModels());

		Map<String, List<IFeature>> featureMaps = new HashMap<>();
		for (IFeatureModel[] featureModels : featureModelsPerLocation) {
			Map<String, List<IFeature>> id2feature = Arrays.stream(featureModels).map(IFeatureModel::getFeature).collect(groupingBy(IFeature::getId));
			id2feature.forEach((id, features) -> featureMaps.computeIfAbsent(id, i -> new ArrayList<>()).add(features.get(features.size() - 1)));
		}
		return featureMaps;
	}

	private static IFeature getFeature(String id, Map<String, List<IFeature>> featureMaps) {
		List<IFeature> features = featureMaps.getOrDefault(id, Collections.emptyList());
		return !features.isEmpty() ? features.get(0) : null;
	}

	private static IPluginModelBase getPlugin(String id, String version, String pluginResolution) {
		ModelEntry modelEntry = PluginRegistry.findEntry(id);
		if (modelEntry != null) {
			return findModel(modelEntry, version, pluginResolution);
		}
		return null;
	}

	private static boolean isWorkspace(String location) {
		if (IPDELauncherConstants.LOCATION_WORKSPACE.equalsIgnoreCase(location)) {
			return true;
		} else if (IPDELauncherConstants.LOCATION_EXTERNAL.equalsIgnoreCase(location)) {
			return false;
		}
		throw new IllegalArgumentException("Unsupported location: " + location); //$NON-NLS-1$
	}

	/**
	 * Finds the best candidate model from the <code>resolution</code> location. If the model is not found there,
	 * alternate location is explored before returning <code>null</code>.
	 * @param modelEntry
	 * @param version
	 * @param location
	 * @return model
	 */
	private static IPluginModelBase findModel(ModelEntry modelEntry, String version, String location) {
		IPluginModelBase model = null;
		if (IPDELauncherConstants.LOCATION_WORKSPACE.equalsIgnoreCase(location)) {
			model = getBestCandidateModel(modelEntry.getWorkspaceModels(), version);
		}
		if (model == null) {
			model = getBestCandidateModel(modelEntry.getExternalModels(), version);
		}
		if (model == null && IPDELauncherConstants.LOCATION_EXTERNAL.equalsIgnoreCase(location)) {
			model = getBestCandidateModel(modelEntry.getWorkspaceModels(), version);
		}
		return model;
	}

	/**
	 * Returns model from the given list that is a 'best match' to the given bundle version or
	 * <code>null</code> if no enabled models were in the provided list.  The best match will
	 * be an exact version match if one is found.  Otherwise a model that is resolved in the
	 * OSGi state with the highest version is returned.
	 *
	 * @param models list of candidate models to choose from
	 * @param version the bundle version to find a match for
	 * @return best candidate model from the list of models or <code>null</code> if no there were no acceptable models in the list
	 */
	private static IPluginModelBase getBestCandidateModel(IPluginModelBase[] models, String version) {
		if (models.length == 0) {
			return null;
		}
		Version requiredVersion = Version.parseVersion(version);
		Comparator<BundleDescription> resolvedBundleVersion = comparing(BundleDescription::isResolved) // false < true  
				.thenComparing(d -> requiredVersion.compareTo(d.getVersion()) == 0) // false < true 
				.thenComparing(BundleDescription::getVersion);
		Comparator<IPluginModelBase> resolvedPluginVersion = comparing(IPluginModelBase::getBundleDescription, resolvedBundleVersion);

		Stream<IPluginModelBase> enabledPlugins = Arrays.stream(models).filter(m -> m.getBundleDescription() != null && m.isEnabled());
		return enabledPlugins.max(resolvedPluginVersion).orElse(null);
	}

	// --- plug-in based launches ---

	private static final BiPredicate<List<Version>, Version> CONTAINS_SAME_VERSION = List::contains;
	private static final BiPredicate<List<Version>, Version> CONTAINS_SAME_MMM_VERSION = (versions, toAdd) -> versions.stream().anyMatch(v -> VersionUtil.compareMacroMinorMicro(toAdd, v) == 0);

	private static Map<IPluginModelBase, String> getWorkspaceBundleMap(ILaunchConfiguration configuration, Map<String, List<Version>> idVersions) throws CoreException {
		Set<String> workspaceBundles = configuration.getAttribute(IPDELauncherConstants.SELECTED_WORKSPACE_BUNDLES, emptySet());

		Map<IPluginModelBase, String> map = getBundleMap(workspaceBundles, ModelEntry::getWorkspaceModels, CONTAINS_SAME_VERSION, idVersions);

		if (configuration.getAttribute(IPDELauncherConstants.AUTOMATIC_ADD, true)) {
			Set<String> deselectedWorkspaceBundles = configuration.getAttribute(IPDELauncherConstants.DESELECTED_WORKSPACE_BUNDLES, emptySet());
			Set<IPluginModelBase> deselectedPlugins = getBundleMap(deselectedWorkspaceBundles, ModelEntry::getWorkspaceModels, null, null).keySet();
			IPluginModelBase[] models = PluginRegistry.getWorkspaceModels();
			for (IPluginModelBase model : models) {
				if (model.getPluginBase().getId() != null && !deselectedPlugins.contains(model) && !map.containsKey(model)) {
					addPlugin(map, model, "default:default", idVersions, CONTAINS_SAME_VERSION); //$NON-NLS-1$
				}
			}
		}
		return map;
	}

	private static Map<IPluginModelBase, String> getTargetBundleMap(ILaunchConfiguration configuration, Map<String, List<Version>> idVersions) throws CoreException {
		Set<String> targetBundles = configuration.getAttribute(IPDELauncherConstants.SELECTED_TARGET_BUNDLES, emptySet());
		return getBundleMap(targetBundles, ModelEntry::getExternalModels, CONTAINS_SAME_MMM_VERSION, idVersions); // don't add same major-minor-micro-version more than once
	}

	private static Map<IPluginModelBase, String> getBundleMap(Set<String> entries, Function<ModelEntry, IPluginModelBase[]> getModels, BiPredicate<List<Version>, Version> versionFilter, Map<String, List<Version>> idVersions) {
		Map<IPluginModelBase, String> map = new LinkedHashMap<>();
		for (String bundleEntry : entries) {
			int index = bundleEntry.indexOf('@');
			if (index < 0) { // if no start levels, assume default
				index = bundleEntry.length();
				bundleEntry += "@default:default"; //$NON-NLS-1$
			}
			String idVersion = bundleEntry.substring(0, index);
			int versionIndex = idVersion.indexOf(VERSION_SEPARATOR);
			String id = (versionIndex > 0) ? idVersion.substring(0, versionIndex) : idVersion;
			String version = (versionIndex > 0) ? idVersion.substring(versionIndex + 1) : null;

			ModelEntry entry = PluginRegistry.findEntry(id);
			if (entry != null) {
				IPluginModelBase[] models = getModels.apply(entry);
				String startData = bundleEntry.substring(index + 1);
				for (IPluginModelBase model : getSelectedModels(models, version, versionFilter == null)) {
					addPlugin(map, model, startData, idVersions, versionFilter);
				}
			}
		}
		return map;
	}

	static final Comparator<IPluginModelBase> VERSION = comparing(BundleLauncherHelper::getVersion);

	private static Iterable<IPluginModelBase> getSelectedModels(IPluginModelBase[] models, String version, boolean greedy) {
		// match only if...
		// a) if we have the same version
		// b) no version (if greedy take latest, else take all)
		// c) all else fails, if there's just one bundle available, use it
		Stream<IPluginModelBase> selectedModels = Arrays.stream(models).filter(IPluginModelBase::isEnabled); // workspace models are always enabled, external might be disabled
		if (version == null) {
			if (!greedy) {
				IPluginModelBase latestModel = selectedModels.max(VERSION).orElseThrow();
				selectedModels = Stream.of(latestModel); // take only  latest
			} // Otherwise be greedy and take all if versionFilter is null
		} else {
			selectedModels = selectedModels.filter(m -> m.getPluginBase().getVersion().equals(version) || models.length == 1);
		}
		return selectedModels::iterator;
	}

	private static void addPlugin(Map<IPluginModelBase, String> map, IPluginModelBase model, String startData, Map<String, List<Version>> idVersions, BiPredicate<List<Version>, Version> containsVersion) {
		if (containsVersion == null) { // be greedy and just take all (idVersions is null as well)
			addBundleToMap(map, model, startData);
		} else {
			List<Version> pluginVersions = idVersions.computeIfAbsent(model.getPluginBase().getId(), n -> new ArrayList<>());
			Version version = getVersion(model);
			if (!containsVersion.test(pluginVersions, version)) { // apply version filter    
				pluginVersions.add(version);
				addBundleToMap(map, model, startData);
			}
		}
	}

	private static Version getVersion(IPluginModelBase model) {
		BundleDescription bundleDescription = model.getBundleDescription();
		if (bundleDescription == null) {
			try {
				return Version.parseVersion(model.getPluginBase().getVersion());
			} catch (IllegalArgumentException e) {
				return Version.emptyVersion;
			}
		}
		return bundleDescription.getVersion();
	}

	/**
	 * Adds the given bundle and start information to the map.  This will override anything set
	 * for system bundles, and set their start level to the appropriate level
	 * @param map The map to add the bundles too
	 * @param bundle The bundle to add
	 * @param substring the start information in the form level:autostart
	 */
	private static void addBundleToMap(Map<IPluginModelBase, String> map, IPluginModelBase bundle, String startData) {
		BundleDescription desc = bundle.getBundleDescription();
		boolean defaultsl = startData == null || startData.equals("default:default"); //$NON-NLS-1$
		if (desc != null && defaultsl) {
			String runLevelText = resolveSystemRunLevelText(bundle);
			String autoText = resolveSystemAutoText(bundle);
			if (runLevelText != null && autoText != null) {
				startData = runLevelText + ":" + autoText; //$NON-NLS-1$
			}
		}
		map.put(bundle, startData);
	}

	private static final Map<String, String> AUTO_STARTED_BUNDLE_LEVELS = Map.ofEntries( //
			Map.entry(IPDEBuildConstants.BUNDLE_DS, "1"), //$NON-NLS-1$
			Map.entry(IPDEBuildConstants.BUNDLE_SIMPLE_CONFIGURATOR, "1"), //$NON-NLS-1$
			Map.entry(IPDEBuildConstants.BUNDLE_EQUINOX_COMMON, "2"), //$NON-NLS-1$
			Map.entry(IPDEBuildConstants.BUNDLE_OSGI, "1"), //$NON-NLS-1$
			Map.entry(IPDEBuildConstants.BUNDLE_CORE_RUNTIME, "default"), //$NON-NLS-1$
			Map.entry(IPDEBuildConstants.BUNDLE_FELIX_SCR, "1")); //$NON-NLS-1$

	public static String resolveSystemRunLevelText(IPluginModelBase model) {
		BundleDescription description = model.getBundleDescription();
		return AUTO_STARTED_BUNDLE_LEVELS.get(description.getSymbolicName());
	}

	public static String resolveSystemAutoText(IPluginModelBase model) {
		BundleDescription description = model.getBundleDescription();
		return AUTO_STARTED_BUNDLE_LEVELS.containsKey(description.getSymbolicName()) ? "true" : null; //$NON-NLS-1$
	}

	public static String writeBundleEntry(IPluginModelBase model, String startLevel, String autoStart) {
		IPluginBase base = model.getPluginBase();
		String id = base.getId();
		StringBuilder buffer = new StringBuilder(id);

		ModelEntry entry = PluginRegistry.findEntry(id);
		if (entry != null) {
			boolean isWorkspacePlugin = model.getUnderlyingResource() != null;
			IPluginModelBase[] entryModels = isWorkspacePlugin ? entry.getWorkspaceModels() : entry.getExternalModels();
			if (entryModels.length > 1) {
				buffer.append(VERSION_SEPARATOR);
				buffer.append(model.getPluginBase().getVersion());
			}
		}

		boolean hasStartLevel = startLevel != null && !startLevel.isEmpty();
		boolean hasAutoStart = autoStart != null && !autoStart.isEmpty();

		if (hasStartLevel || hasAutoStart) {
			buffer.append('@');
			if (hasStartLevel) {
				buffer.append(startLevel);
			}
			buffer.append(':');
			if (hasAutoStart) {
				buffer.append(autoStart);
			}
		}
		return buffer.toString();
	}

	@SuppressWarnings("deprecation")
	public static void migrateLaunchConfiguration(ILaunchConfigurationWorkingCopy configuration) throws CoreException {

		String value = configuration.getAttribute("wsproject", (String) null); //$NON-NLS-1$
		if (value != null) {
			configuration.setAttribute("wsproject", (String) null); //$NON-NLS-1$
			if (value.indexOf(';') != -1) {
				value = value.replace(';', ',');
			} else if (value.indexOf(':') != -1) {
				value = value.replace(':', ',');
			}
			value = (value.length() == 0 || value.equals(",")) //$NON-NLS-1$
					? null
					: value.substring(0, value.length() - 1);

			boolean automatic = configuration.getAttribute(IPDELauncherConstants.AUTOMATIC_ADD, true);
			String attr = automatic ? IPDELauncherConstants.DESELECTED_WORKSPACE_PLUGINS : IPDELauncherConstants.SELECTED_WORKSPACE_PLUGINS;
			configuration.setAttribute(attr, value);
		}

		String value2 = configuration.getAttribute("extplugins", (String) null); //$NON-NLS-1$
		if (value2 != null) {
			configuration.setAttribute("extplugins", (String) null); //$NON-NLS-1$
			if (value2.indexOf(';') != -1) {
				value2 = value2.replace(';', ',');
			} else if (value2.indexOf(':') != -1) {
				value2 = value2.replace(':', ',');
			}
			value2 = (value2.length() == 0 || value2.equals(",")) ? null : value2.substring(0, value2.length() - 1); //$NON-NLS-1$
			configuration.setAttribute(IPDELauncherConstants.SELECTED_TARGET_PLUGINS, value2);
		}

		convertToSet(configuration, IPDELauncherConstants.SELECTED_TARGET_PLUGINS, IPDELauncherConstants.SELECTED_TARGET_BUNDLES);
		convertToSet(configuration, IPDELauncherConstants.SELECTED_WORKSPACE_PLUGINS, IPDELauncherConstants.SELECTED_WORKSPACE_BUNDLES);
		convertToSet(configuration, IPDELauncherConstants.DESELECTED_WORKSPACE_PLUGINS, IPDELauncherConstants.DESELECTED_WORKSPACE_BUNDLES);

		String version = configuration.getAttribute(IPDEConstants.LAUNCHER_PDE_VERSION, (String) null);
		boolean newApp = TargetPlatformHelper.usesNewApplicationModel();
		boolean upgrade = !"3.3".equals(version) && newApp; //$NON-NLS-1$
		if (!upgrade) {
			upgrade = TargetPlatformHelper.getTargetVersion() >= 3.2 && version == null;
		}
		if (upgrade) {
			configuration.setAttribute(IPDEConstants.LAUNCHER_PDE_VERSION, newApp ? "3.3" : "3.2a"); //$NON-NLS-1$ //$NON-NLS-2$
			boolean usedefault = configuration.getAttribute(IPDELauncherConstants.USE_DEFAULT, true);
			boolean automaticAdd = configuration.getAttribute(IPDELauncherConstants.AUTOMATIC_ADD, true);
			if (!usedefault) {
				ArrayList<String> list = new ArrayList<>();
				if (version == null) {
					list.add("org.eclipse.core.contenttype"); //$NON-NLS-1$
					list.add("org.eclipse.core.jobs"); //$NON-NLS-1$
					list.add(IPDEBuildConstants.BUNDLE_EQUINOX_COMMON);
					list.add("org.eclipse.equinox.preferences"); //$NON-NLS-1$
					list.add("org.eclipse.equinox.registry"); //$NON-NLS-1$
				}
				if (!"3.3".equals(version) && newApp) { //$NON-NLS-1$
					list.add("org.eclipse.equinox.app"); //$NON-NLS-1$
				}
				Set<String> extensions = new LinkedHashSet<>(configuration.getAttribute(IPDELauncherConstants.SELECTED_WORKSPACE_BUNDLES, emptySet()));
				Set<String> target = new LinkedHashSet<>(configuration.getAttribute(IPDELauncherConstants.SELECTED_TARGET_BUNDLES, emptySet()));
				for (String plugin : list) {
					IPluginModelBase model = PluginRegistry.findModel(plugin);
					if (model == null) {
						continue;
					}
					if (model.getUnderlyingResource() != null) {
						if (!automaticAdd) {
							extensions.add(plugin);
						}
					} else {
						target.add(plugin);
					}
				}
				if (!extensions.isEmpty()) {
					configuration.setAttribute(IPDELauncherConstants.SELECTED_WORKSPACE_BUNDLES, extensions);
				}
				if (!target.isEmpty()) {
					configuration.setAttribute(IPDELauncherConstants.SELECTED_TARGET_BUNDLES, target);
				}
			}
		}
	}

	private static ILaunchConfigurationWorkingCopy getWorkingCopy(ILaunchConfiguration configuration) throws CoreException {
		if (configuration.isWorkingCopy()) {
			return (ILaunchConfigurationWorkingCopy) configuration;
		}
		return configuration.getWorkingCopy();
	}

	@SuppressWarnings("deprecation")
	public static void migrateOsgiLaunchConfiguration(ILaunchConfigurationWorkingCopy configuration) throws CoreException {
		convertToSet(configuration, IPDELauncherConstants.WORKSPACE_BUNDLES, IPDELauncherConstants.SELECTED_WORKSPACE_BUNDLES);
		convertToSet(configuration, IPDELauncherConstants.TARGET_BUNDLES, IPDELauncherConstants.SELECTED_TARGET_BUNDLES);
		convertToSet(configuration, IPDELauncherConstants.DESELECTED_WORKSPACE_PLUGINS, IPDELauncherConstants.DESELECTED_WORKSPACE_BUNDLES);
	}

	private static void convertToSet(ILaunchConfigurationWorkingCopy wc, String stringAttribute, String listAttribute) throws CoreException {
		String value = wc.getAttribute(stringAttribute, (String) null);
		if (value != null) {
			wc.removeAttribute(stringAttribute);
			String[] itemArray = value.split(","); //$NON-NLS-1$
			Set<String> itemSet = new HashSet<>(Arrays.asList(itemArray));
			wc.setAttribute(listAttribute, itemSet);
		}
	}

	/**
	 * Returns a map of IPluginModelBase to their associated String resolution setting. Reads the
	 * additional plug-ins attribute of the given launch config and returns a map of plug-in models
	 * to their resolution.  The attribute stores the id, version, enablement and resolution of each plug-in.
	 * The models to be returned are determined by trying to find a model with a matching name, matching version
	 * (or highest) in the resolution location (falling back on other locations if the chosen option is unavailable).
	 * The includeDisabled option allows the returned list to contain only plug-ins that are enabled (checked) in
	 * the config.
	 *
	 * @param config launch config to read attribute from
	 * @param onlyEnabled whether all plug-ins in the attribute should be returned or just the ones marked as enabled/checked
	 * @return map of IPluginModelBase to String resolution setting
	 * @throws CoreException if there is a problem reading the launch config
	 */
	public static Map<IPluginModelBase, AdditionalPluginData> getAdditionalPlugins(ILaunchConfiguration config, boolean onlyEnabled) throws CoreException {
		Map<IPluginModelBase, AdditionalPluginData> resolvedAdditionalPlugins = new HashMap<>();
		Set<String> userAddedPlugins = config.getAttribute(IPDELauncherConstants.ADDITIONAL_PLUGINS, emptySet());
		String defaultPluginResolution = config.getAttribute(IPDELauncherConstants.FEATURE_PLUGIN_RESOLUTION, IPDELauncherConstants.LOCATION_WORKSPACE);

		for (String addedPlugin : userAddedPlugins) {
			String[] pluginData = addedPlugin.split(":"); //$NON-NLS-1$
			boolean checked = Boolean.parseBoolean(pluginData[3]);
			if (!onlyEnabled || checked) {
				String id = pluginData[0];
				String version = pluginData[1];
				String pluginResolution = pluginData[2];
				ModelEntry pluginModelEntry = PluginRegistry.findEntry(id);

				if (pluginModelEntry != null) {
					if (IPDELauncherConstants.LOCATION_DEFAULT.equalsIgnoreCase(pluginResolution)) {
						pluginResolution = defaultPluginResolution;
					}
					IPluginModelBase model = findModel(pluginModelEntry, version, pluginResolution);
					if (model != null) {
						String startLevel = (pluginData.length >= 6) ? pluginData[4] : null;
						String autoStart = (pluginData.length >= 6) ? pluginData[5] : null;
						AdditionalPluginData additionalPluginData = new AdditionalPluginData(pluginData[2], checked, startLevel, autoStart);
						resolvedAdditionalPlugins.put(model, additionalPluginData);
					}
				}
			}
		}
		return resolvedAdditionalPlugins;
	}

	public static class AdditionalPluginData {
		public final String fResolution;
		public final boolean fEnabled;
		public final String fStartLevel;
		public final String fAutoStart;

		public AdditionalPluginData(String resolution, boolean enabled, String startLevel, String autoStart) {
			fResolution = resolution;
			fEnabled = enabled;
			fStartLevel = (startLevel == null || startLevel.isEmpty()) ? "default" : startLevel; //$NON-NLS-1$
			fAutoStart = (autoStart == null || autoStart.isEmpty()) ? "default" : autoStart; //$NON-NLS-1$
		}

		String startLevels() {
			return fStartLevel + ':' + fAutoStart;
		}
	}
}
