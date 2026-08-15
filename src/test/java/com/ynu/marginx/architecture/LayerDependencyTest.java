package com.ynu.marginx.architecture;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures.LayeredArchitecture;

@AnalyzeClasses(packages = "com.ynu.marginx", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerDependencyTest {

    @ArchTest
    static final LayeredArchitecture LAYERS = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("presentation").definedBy("com.ynu.marginx.presentation..")
            .layer("application").definedBy("com.ynu.marginx.application..")
            .layer("domain").definedBy("com.ynu.marginx.domain..")
            .layer("infrastructure").definedBy("com.ynu.marginx.infrastructure..")
            .layer("shared").definedBy("com.ynu.marginx.shared..")
            .whereLayer("presentation").mayNotBeAccessedByAnyLayer()
            .whereLayer("application").mayOnlyBeAccessedByLayers("presentation")
            .whereLayer("infrastructure").mayOnlyBeAccessedByLayers("presentation")
            .whereLayer("domain").mayOnlyBeAccessedByLayers("presentation", "application", "infrastructure");

    @ArchTest
    static final ArchRule DOMAIN_IS_TECHNOLOGY_FREE = com.tngtech.archunit.lang.syntax.ArchRuleDefinition
            .noClasses().that().resideInAPackage("com.ynu.marginx.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.ynu.marginx.infrastructure..",
                    "com.ynu.marginx.application..",
                    "com.ynu.marginx.presentation..",
                    "java.nio.file..",
                    "java.io..",
                    "picocli..")
            .because("the domain must not know about files, the console or the simulator binary");

    @ArchTest
    static final ArchRule NO_CYCLES = slices()
            .matching("com.ynu.marginx.(*)..")
            .should().beFreeOfCycles();
}
