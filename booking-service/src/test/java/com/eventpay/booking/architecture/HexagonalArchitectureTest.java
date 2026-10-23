package com.eventpay.booking.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Hexagonal estricta en booking-service (constitución, principio 3; RNF2). */
@AnalyzeClasses(packages = "com.eventpay.booking")
class HexagonalArchitectureTest {

  @ArchTest
  static final ArchRule domain_does_not_depend_on_spring =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..");

  @ArchTest
  static final ArchRule domain_does_not_depend_on_persistence_or_web_frameworks =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("jakarta..", "javax..", "org.hibernate..");

  @ArchTest
  static final ArchRule domain_does_not_depend_on_outer_layers =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..application..", "..infrastructure..");

  @ArchTest
  static final ArchRule domain_classes_are_not_annotated_with_spring_annotations =
      classes()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .notBeAnnotatedWith(springAnnotation());

  /** Cualquier anotación de Spring cuenta, no solo los estereotipos de {@code @Component}. */
  private static DescribedPredicate<JavaAnnotation<?>> springAnnotation() {
    return new DescribedPredicate<>("anotación de Spring") {
      @Override
      public boolean test(JavaAnnotation<?> annotation) {
        return annotation.getRawType().getName().startsWith("org.springframework");
      }
    };
  }
}
