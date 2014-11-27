package org.jake.depmanagement.ivy;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.descriptor.Configuration.Visibility;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.jake.depmanagement.JakeArtifact;
import org.jake.depmanagement.JakeDependencies;
import org.jake.depmanagement.JakeExternalModule;
import org.jake.depmanagement.JakeModuleId;
import org.jake.depmanagement.JakeRepo;
import org.jake.depmanagement.JakeRepos;
import org.jake.depmanagement.JakeScope;
import org.jake.depmanagement.JakeScopeMapping;
import org.jake.depmanagement.JakeScopedDependency;
import org.jake.depmanagement.JakeScopedDependency.ScopeType;
import org.jake.depmanagement.JakeVersion;
import org.jake.depmanagement.JakeVersionRange;
import org.jake.depmanagement.JakeVersionedModule;
import org.jake.utils.JakeUtilsString;

final class Translations {

	/**
	 * Stands for the default configuration for publishing in ivy.
	 */
	static final JakeScope DEFAULT_CONFIGURATION = JakeScope.of("default");

	private Translations() {}

	public static DefaultModuleDescriptor toUnpublished(JakeVersionedModule module, JakeDependencies dependencies, JakeScope defaultScope, JakeScopeMapping defaultMapping) {
		final ModuleRevisionId thisModuleRevisionId = ModuleRevisionId
				.newInstance(module.moduleId().group(), module.moduleId().name(), module.version().name());
		final DefaultModuleDescriptor moduleDescriptor = new DefaultModuleDescriptor(thisModuleRevisionId, "integration", null);

		populateModuleDescriptor(moduleDescriptor, dependencies, defaultScope, defaultMapping);
		return moduleDescriptor;

	}

	/**
	 * @param scopedDependency must be of {@link JakeExternalModule}
	 */
	private static DependencyDescriptor to(JakeScopedDependency scopedDependency, JakeScope defaultScope, JakeScopeMapping defaultMapping) {
		final JakeExternalModule externalModule = (JakeExternalModule) scopedDependency.dependency();
		final DefaultDependencyDescriptor result =  new DefaultDependencyDescriptor(to(externalModule), false);

		// filling configuration
		if (scopedDependency.scopeType() == ScopeType.SIMPLE) {
			for (final JakeScope scope : scopedDependency.scopes()) {
				final JakeScopeMapping mapping = resolveSimple(scope, defaultScope, defaultMapping);
				for (final JakeScope fromScope : mapping.entries()) {
					for (final JakeScope mappedScope : mapping.mappedScopes(fromScope)) {
						result.addDependencyConfiguration(fromScope.name(), mappedScope.name());
					}
				}

			}
		} else if (scopedDependency.scopeType() == ScopeType.MAPPED) {
			for (final JakeScope scope : scopedDependency.scopeMapping().entries()) {
				for (final JakeScope mappedScope : scopedDependency.scopeMapping().mappedScopes(scope)) {
					result.addDependencyConfiguration(scope.name(), mappedScope.name());
				}
			}
		} else {
			if (defaultMapping != null) {
				for (final JakeScope entryScope : defaultMapping.entries()) {
					for (final JakeScope mappedScope : defaultMapping.mappedScopes(entryScope)) {
						result.addDependencyConfiguration(entryScope.name(), mappedScope.name());
					}
				}
			} else if (defaultScope != null) {
				result.addDependencyConfiguration(DEFAULT_CONFIGURATION.name(), defaultScope.name());
			}
		}
		return result;
	}

	public static Configuration to(JakeScope jakeScope) {
		final List<String> extendedScopes = new LinkedList<String>();
		for (final JakeScope parent : jakeScope.extendedScopes()) {
			extendedScopes.add(parent.name());
		}
		final Visibility visibility = jakeScope.isPublic() ? Visibility.PUBLIC : Visibility.PRIVATE;
		return new Configuration(jakeScope.name(), visibility, jakeScope.description(), extendedScopes.toArray(new String[0]), jakeScope.transitive(), null);
	}

	private static ModuleRevisionId to(JakeExternalModule externalModule) {
		return new ModuleRevisionId(to(externalModule.moduleId()), to(externalModule.versionRange()));
	}

	private static ModuleId to(JakeModuleId moduleId) {
		return new ModuleId(moduleId.group(), moduleId.name());
	}

	private static String to(JakeVersionRange versionRange) {
		return versionRange.definition();
	}

	private static DependencyResolver to(JakeRepo repo) {
		if (repo instanceof JakeRepo.MavenRepository) {
			final IBiblioResolver result = new IBiblioResolver();
			result.setM2compatible(true);
			result.setUseMavenMetadata(true);
			result.setRoot(repo.url().toString());
			result.setUsepoms(true);
			return result;
		}
		throw new IllegalStateException(repo.getClass().getName() + " not handled by translator.");
	}

	public static void populateIvySettingsWithRepo(IvySettings ivySettings, JakeRepos repos) {
		final boolean ivyHasYetDefaultResolver = ivySettings.getDefaultResolver() != null;
		boolean first = true;
		for(final JakeRepo jakeRepo : repos) {
			final DependencyResolver resolver = to(jakeRepo);
			resolver.setName(jakeRepo.toString());
			ivySettings.addResolver(resolver);
			if (first && !ivyHasYetDefaultResolver) {
				ivySettings.setDefaultResolver(resolver.getName());
			}
			first = false;
		}
	}

	public static JakeArtifact to(Artifact artifact, File localFile) {
		final JakeModuleId moduleId = JakeModuleId.of(artifact.getModuleRevisionId().getOrganisation(),
				artifact.getModuleRevisionId().getName());
		final JakeVersionedModule module = JakeVersionedModule.of(moduleId,
				JakeVersion.of(artifact.getModuleRevisionId().getRevision()));
		return JakeArtifact.of(module, localFile);
	}

	private static String toIvyExpression(JakeScopeMapping scopeMapping) {
		final List<String> list = new LinkedList<String>();
		for (final JakeScope scope : scopeMapping.entries()) {
			final List<String> targets = new LinkedList<String>();
			for (final JakeScope target : scopeMapping.mappedScopes(scope)) {
				targets.add(target.name());
			}
			final String item = scope.name() + " -> " + JakeUtilsString.join(targets, ",");
			list.add(item);
		}
		return JakeUtilsString.join(list, "; ");
	}

	private static void populateModuleDescriptor(DefaultModuleDescriptor moduleDescriptor, JakeDependencies dependencies, JakeScope defaultScope, JakeScopeMapping defaultMapping) {


		// Add configuration definitions
		for (final JakeScope involvedScope : dependencies.moduleScopes()) {
			final Configuration configuration = to(involvedScope);
			moduleDescriptor.addConfiguration(configuration);
		}
		if (defaultScope != null) {
			moduleDescriptor.setDefaultConf(defaultScope.name());
		} else if (defaultMapping != null) {
			moduleDescriptor.setDefaultConfMapping(toIvyExpression(defaultMapping));
		}

		// Add dependencies
		for (final JakeScopedDependency scopedDependency : dependencies) {
			final DependencyDescriptor dependencyDescriptor = to(scopedDependency, defaultScope, defaultMapping);
			moduleDescriptor.addDependency(dependencyDescriptor);
		}

	}

	private static JakeScopeMapping resolveSimple(JakeScope scope, JakeScope defaultScope, JakeScopeMapping defaultMapping) {
		final JakeScopeMapping result;
		if (scope == null) {
			if (defaultScope == null) {
				if (defaultMapping == null) {
					result = JakeScopeMapping.of(JakeScope.of("default")).to("default");
				} else {
					result = defaultMapping;
				}
			} else {
				if (defaultMapping == null) {
					result = JakeScopeMapping.of(defaultScope).to(defaultScope);
				} else {
					result = JakeScopeMapping.of(defaultScope).to(defaultMapping.mappedScopes(defaultScope));
				}

			}
		} else {
			if (defaultMapping == null) {
				result = JakeScopeMapping.of(scope).to(scope);
			} else {
				if (defaultMapping.entries().contains(scope)) {
					result = JakeScopeMapping.of(scope).to(defaultMapping.mappedScopes(scope));
				} else {
					result = scope.mapTo(scope);
				}

			}
		}
		return result;

	}

}
