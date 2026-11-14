package com.sanaa.auth.bootstrap;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.sanaa.auth.domain.MembershipRole;
import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantType;
import com.sanaa.common.tenant.TenantContext;

/**
 * Demo data for local walkthroughs. Only registered with the "demo" profile and never contains a
 * password: the password comes from DEMO_PASSWORD, and a missing value fails startup with an
 * explicit message.
 *
 * <p>All user-facing data is French. The publisher pages are public, so these rows are what a
 * reviewer sees first; the accounts cover both publisher types and all four roles.
 */
@Component
@Profile("demo")
public class DemoSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeed.class);

    private static final List<DemoPublisher> PUBLISHERS = List.of(
            new DemoPublisher("atlas", "Atlas Academy", TenantType.ORGANIZATION, "#00B42A",
                    "Apprenez auprès de créateurs passionnés",
                    "Atlas Academy réunit des formateurs indépendants autour du développement web, "
                            + "de la data et de l'intelligence artificielle.",
                    "contact@atlas.local",
                    List.of(
                            new DemoSeedService.SeedAccount("admin@atlas.local", "Atlas Admin",
                                    MembershipRole.TENANT_ADMIN, "Responsable pédagogique",
                                    "Je coordonne les formations et accompagne les formateurs "
                                            + "d'Atlas Academy.", null, null),
                            new DemoSeedService.SeedAccount("instructor1@atlas.local",
                                    "Yasmine El Amrani", MembershipRole.INSTRUCTOR,
                                    "Formatrice en développement web",
                                    "Développeuse full-stack depuis dix ans, j'enseigne JavaScript, "
                                            + "React et les bonnes pratiques du code accessible.",
                                    null, null),
                            new DemoSeedService.SeedAccount("instructor2@atlas.local",
                                    "Omar Benjelloun", MembershipRole.INSTRUCTOR,
                                    "Formateur en data et intelligence artificielle",
                                    "Data scientist, j'accompagne les débutants vers leur premier "
                                            + "modèle d'apprentissage automatique.",
                                    null, null)),
                    List.of("student1@atlas.local", "student2@atlas.local")),
            new DemoPublisher("acme", "Studio Acme", TenantType.INDIVIDUAL, "#722ED1",
                    "Studio de création visuelle indépendant",
                    "Studio Acme conçoit des identités visuelles et des interfaces élégantes. "
                            + "Une formation, une formatrice, un accompagnement direct.",
                    "contact@acme.local",
                    List.of(new DemoSeedService.SeedAccount("admin@acme.local", "Sofia Acme",
                            MembershipRole.TENANT_ADMIN, "Directrice artistique",
                            "Designer graphique et directrice artistique, je partage mon expérience "
                                    + "du branding et du design d'interface.",
                            "https://instagram.com/studio.acme.demo",
                            "https://www.linkedin.com/company/studio-acme-demo")),
                    List.of("instructor@acme.local", "student1@acme.local",
                            "student2@acme.local")));

    private static final List<DemoSeedService.SeedLearner> LEARNERS = List.of(
            new DemoSeedService.SeedLearner("learner1@example.local", "Amine Tazi",
                    "Apprenant en développement web",
                    "Je me forme au développement web pour changer de métier."),
            new DemoSeedService.SeedLearner("learner2@example.local", "Salma Idrissi",
                    "Apprenante en design d'interface",
                    "Je découvre le design d'interface et la création d'identités visuelles."),
            new DemoSeedService.SeedLearner("learner3@example.local", "Youssef Alaoui",
                    "Apprenant en data",
                    "Je veux comprendre la data et l'intelligence artificielle par la pratique."),
            new DemoSeedService.SeedLearner("learner4@example.local", "Nadia Chraibi",
                    "Apprenante curieuse",
                    "J'explore les formations pour développer de nouvelles compétences."));

    private final Environment environment;
    private final DemoSeedService demoSeedService;

    public DemoSeed(Environment environment, DemoSeedService demoSeedService) {
        this.environment = environment;
        this.demoSeedService = demoSeedService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String password = environment.getProperty("DEMO_PASSWORD");
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    "DEMO_PASSWORD must be set when the demo profile is active");
        }

        for (DemoPublisher demo : PUBLISHERS) {
            Tenant tenant = demoSeedService.ensureTenant(demo.slug(), demo.name(), demo.type(),
                    demo.color(), demo.headline(), demo.description(), demo.contactEmail());
            TenantContext.runAs(tenant.getId(), () -> {
                demoSeedService.ensurePublisherAccounts(demo.accounts(), password);
                demoSeedService.removeAccounts(demo.obsoleteEmails());
            });
        }
        demoSeedService.ensureLearners(LEARNERS, password);
        log.info("Demo publishers, accounts and learners are ready");
    }

    private record DemoPublisher(String slug, String name, TenantType type, String color,
                                 String headline, String description, String contactEmail,
                                 List<DemoSeedService.SeedAccount> accounts,
                                 List<String> obsoleteEmails) {
    }
}
