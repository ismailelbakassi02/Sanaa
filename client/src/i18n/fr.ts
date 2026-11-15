import type { CourseStatus } from '../api/courses';
import type { PaymentMethod, PaymentStatus } from '../api/payments';
import type { MembershipRole, PublisherType, PublisherStatus, Role, UserStatus } from '../api/types';

/**
 * Every user-visible string of the application lives here. Components never
 * hardcode French text. Hidden course and payment pages keep their keys even
 * though their routes are not exposed until the marketplace tasks.
 */
export type ErrorContext =
  | 'login'
  | 'register'
  | 'create-publisher'
  | 'slug'
  | 'team'
  | 'profile'
  | 'password'
  | 'default';

export const fr = {
  documentTitle: 'SANAA',
  documentTitleTemplate: (page: string) => `${page} · SANAA`,

  app: {
    name: 'SANAA',
    tagline: "Apprenez auprès de créateurs indépendants et d'organisations qui partagent leurs savoirs.",
    panelCaption:
      'Un seul compte pour suivre des cours, acheter auprès de plusieurs créateurs et gérer votre studio.',
    headerTagline: 'La plateforme des créateurs de savoir',
  },

  common: {
    loading: 'Chargement…',
    retry: 'Réessayer',
    refresh: 'Actualiser',
    required: 'Ce champ est requis',
    emailInvalid: 'Adresse e-mail invalide',
    passwordPolicy: 'Le mot de passe doit contenir entre 10 et 72 octets.',
    passwordMin: 'Le mot de passe doit contenir au moins 10 octets',
    passwordMax: 'Le mot de passe doit contenir au plus 72 octets',
    save: 'Enregistrer',
    saving: 'Enregistrement…',
    cancel: 'Annuler',
    confirm: 'Confirmer',
    close: 'Fermer',
    search: 'Rechercher',
    reset: 'Réinitialiser',
    actions: 'Actions',
    optional: 'facultatif',
    readOnly: 'Lecture seule',
    none: 'Aucun',
    soon: 'Bientôt disponible',
    back: 'Retour',
    backHome: "Retour à l'accueil",
    unknownError: 'Une erreur est survenue, réessayez plus tard.',
  },

  theme: {
    toDark: 'Passer en mode sombre',
    toLight: 'Passer en mode clair',
  },

  nav: {
    label: 'Navigation principale',
    home: 'Accueil',
    me: 'Mon espace',
    studioProfile: 'Profil du studio',
    studioTeam: 'Équipe',
    admin: 'Administration',
    becomeCreator: 'Devenir créateur',
    privacy: 'Confidentialité',
    signIn: 'Connexion',
    signUp: 'Créer un compte',
    userMenu: 'Menu utilisateur',
    account: 'Mon espace',
    password: 'Mot de passe',
    logout: 'Se déconnecter',
    openMenu: 'Ouvrir le menu',
    closeMenu: 'Fermer le menu',
  },

  roles: {
    STUDENT: 'Apprenant',
    INSTRUCTOR: 'Formateur',
    TENANT_ADMIN: 'Administrateur',
    SUPER_ADMIN: 'Administrateur plateforme',
  } satisfies Record<Role, string>,

  publisherTypes: {
    INDIVIDUAL: 'Créateur indépendant',
    ORGANIZATION: 'Organisation',
  } satisfies Record<PublisherType, string>,

  membershipRoles: {
    TENANT_ADMIN: 'Administrateur',
    INSTRUCTOR: 'Formateur',
  } satisfies Record<MembershipRole, string>,

  platformRoles: {
    USER: 'Utilisateur',
    SUPER_ADMIN: 'Administrateur plateforme',
  } satisfies Record<'USER' | 'SUPER_ADMIN', string>,

  accountStatuses: {
    ACTIVE: 'Actif',
    DISABLED: 'Désactivé',
    SUSPENDED: 'Suspendu',
  } satisfies Record<UserStatus | PublisherStatus, string>,

  home: {
    heroTitle: 'Apprenez auprès de ceux qui pratiquent.',
    heroLead:
      "SANAA réunit des créateurs indépendants et des organisations qui enseignent leur métier. Un compte suffit pour découvrir leurs cours, suivre votre progression et gérer votre propre studio.",
    startLearning: 'Créer mon compte',
    becomeCreator: 'Devenir créateur',
    openSpace: 'Accéder à mon espace',
    learnerTitle: 'Pour les apprenants',
    learnerLead:
      "Un compte global, des achats auprès de plusieurs créateurs et un espace personnel pour retrouver vos formations.",
    learnerPoints: [
      'Un seul compte pour tous les créateurs de la plateforme.',
      'Des paiements suivis, avec un justificatif et une validation.',
      'Un espace personnel qui rassemble vos cours et vos informations.',
    ],
    creatorTitle: 'Pour les créateurs',
    creatorLead:
      'Ouvrez un studio privé, publiez vos cours et gérez votre équipe, vos paiements et votre page publique.',
    creatorPoints: [
      'Un studio isolé : vos données ne sont jamais visibles par un autre créateur.',
      'Une page publique à votre nom, avec votre identité et vos liens.',
      'Une équipe de formateurs pour les organisations.',
    ],
    calloutTitle: 'Un studio, une équipe, une page publique.',
    calloutBody:
      "Créez votre espace en quelques minutes. Vous restez propriétaire de votre contenu et de votre relation avec les apprenants.",
    calloutAction: 'Ouvrir mon studio',
    privacyLink: 'Comment nous protégeons vos données',
  },

  auth: {
    login: {
      title: 'Connexion',
      subtitle: 'Accédez à votre espace SANAA avec votre adresse e-mail.',
      emailLabel: 'E-mail',
      emailPlaceholder: 'vous@exemple.com',
      passwordLabel: 'Mot de passe',
      passwordPlaceholder: 'Votre mot de passe',
      submit: 'Se connecter',
      submitting: 'Connexion…',
      noAccount: 'Pas encore de compte ?',
      createAccount: 'Créer un compte',
      creatorQuestion: 'Vous souhaitez enseigner ?',
      becomeCreator: 'Devenir créateur',
      success: 'Connexion réussie.',
    },
    register: {
      title: 'Créer un compte',
      subtitle: 'Un compte gratuit pour suivre les cours de tous les créateurs SANAA.',
      nameLabel: 'Nom complet',
      namePlaceholder: 'Prénom Nom',
      emailLabel: 'E-mail',
      emailPlaceholder: 'vous@exemple.com',
      passwordLabel: 'Mot de passe',
      passwordPlaceholder: '10 à 72 octets',
      confirmLabel: 'Confirmer le mot de passe',
      confirmPlaceholder: 'Saisissez à nouveau le mot de passe',
      submit: 'Créer mon compte',
      submitting: 'Création…',
      haveAccount: 'Vous avez déjà un compte ?',
      signIn: 'Se connecter',
      passwordMismatch: 'Les mots de passe ne correspondent pas.',
      success: 'Compte créé, bienvenue !',
      creatorHint: 'Vous vendez déjà des formations ?',
      becomeCreator: 'Ouvrir un studio',
    },
  },

  password: {
    title: 'Mot de passe',
    subtitle: 'Choisissez un nouveau mot de passe pour sécuriser votre compte.',
    forcedTitle: 'Changement de mot de passe requis',
    forcedBody:
      'Votre compte a été créé avec un mot de passe temporaire. Choisissez un nouveau mot de passe pour accéder au reste de SANAA.',
    currentLabel: 'Mot de passe actuel',
    currentPlaceholder: 'Votre mot de passe actuel',
    newLabel: 'Nouveau mot de passe',
    newPlaceholder: '10 à 72 octets',
    confirmLabel: 'Confirmer le nouveau mot de passe',
    confirmPlaceholder: 'Saisissez à nouveau le nouveau mot de passe',
    submit: 'Enregistrer le mot de passe',
    submitting: 'Enregistrement…',
    success: 'Mot de passe mis à jour.',
    mismatch: 'Les mots de passe ne correspondent pas.',
    same: "Le nouveau mot de passe doit être différent de l'actuel.",
    logout: 'Se déconnecter',
  },

  becomeCreator: {
    title: 'Devenir créateur',
    subtitle:
      "Ouvrez votre studio SANAA : un espace privé pour publier vos cours, gérer votre équipe et recevoir vos paiements.",
    stepAccount: 'Votre compte',
    stepPublisher: 'Votre studio',
    accountTitle: 'Votre compte',
    accountBody:
      'Vous pouvez ouvrir un studio avec un nouveau compte ou avec le compte apprenant déjà connecté.',
    authenticatedAs: (name: string, email: string) => `Connecté en tant que ${name} (${email}).`,
    accountConflictTitle: 'Vous avez déjà un espace créateur',
    accountConflictBody:
      'Un compte ne peut être rattaché qu’à un seul créateur. Utilisez le studio existant ou un autre compte.',
    accountConflictAction: 'Ouvrir mon studio',
    accountAdminTitle: 'Compte administrateur plateforme',
    accountAdminBody:
      "Un compte d'administration de la plateforme ne peut pas ouvrir de studio créateur. Utilisez un compte apprenant distinct.",
    accountAdminAction: 'Retour à mon espace',
    ownerTitle: 'Créer mon compte propriétaire',
    ownerBody: 'Ces informations servent à vous connecter ensuite au studio.',
    fullNameLabel: 'Nom complet',
    fullNamePlaceholder: 'Prénom Nom',
    emailLabel: 'E-mail',
    emailPlaceholder: 'vous@exemple.com',
    passwordLabel: 'Mot de passe',
    passwordPlaceholder: '10 à 72 octets',
    confirmLabel: 'Confirmer le mot de passe',
    confirmPlaceholder: 'Saisissez à nouveau le mot de passe',
    continue: 'Continuer',
    back: 'Retour',
    publisherTitle: 'Votre studio',
    publisherBody:
      'Ces informations apparaissent sur votre page publique. Vous pourrez les modifier plus tard.',
    typeLabel: 'Type de créateur',
    typeIndividual: 'Créateur indépendant',
    typeIndividualHint: 'Vous enseignez seul, sans équipe.',
    typeOrganization: 'Organisation',
    typeOrganizationHint: 'Une structure avec plusieurs formateurs.',
    nameLabel: 'Nom du créateur',
    namePlaceholder: 'Ex. Atelier Lumière',
    slugLabel: 'Adresse publique',
    slugHint: 'Lettres minuscules, chiffres et tirets, de 3 à 63 caractères.',
    slugPrefix: '/publishers/',
    slugChecking: 'Vérification…',
    slugAvailable: 'Cette adresse est disponible.',
    slugTaken: 'Cette adresse est déjà prise.',
    slugInvalid: 'Adresse invalide : 3 à 63 caractères, lettres minuscules, chiffres ou tirets.',
    colorLabel: 'Couleur du studio',
    colorHint: 'Elle colore votre page publique et les boutons de votre studio.',
    contactLabel: 'E-mail de contact',
    contactHint: 'Utilisé par SANAA pour vous joindre au sujet de vos paiements.',
    submit: 'Ouvrir mon studio',
    submitting: 'Création du studio…',
    successAnonymous: 'Studio créé. Connectez-vous avec votre e-mail pour continuer.',
    successStudio: 'Studio créé, bienvenue !',
    validation: {
      name: 'Le nom doit contenir entre 2 et 120 caractères.',
      slug: 'Choisissez une adresse valide et disponible.',
      color: 'Choisissez une couleur au format #RRGGBB.',
      contact: 'Adresse e-mail invalide.',
      ownerName: 'Le nom doit contenir entre 2 et 120 caractères.',
      password: 'Le mot de passe doit contenir entre 10 et 72 octets.',
      confirm: 'Les mots de passe ne correspondent pas.',
    },
  },

  images: {
    wrongType: 'Format non pris en charge : utilisez une image PNG ou JPEG.',
    tooLarge: "L'image dépasse 1 Mo.",
    uploaded: 'Image enregistrée.',
    placeholder: 'Aucune image',
    choose: 'Choisir une image',
    replace: 'Remplacer',
    remove: 'Retirer',
    hint: 'Image PNG ou JPEG, 1 Mo maximum.',
  },

  validation: {
    email: 'Adresse e-mail invalide.',
    password: 'Le mot de passe doit contenir entre 10 et 72 octets.',
    name: 'Le nom doit contenir entre 2 et 120 caractères.',
    slug: 'Adresse invalide : 3 à 63 caractères, lettres minuscules, chiffres ou tirets.',
    color: 'Choisissez une couleur au format #RRGGBB.',
    url: 'Utilisez une adresse HTTPS complète, sans identifiant incorporé.',
    tooLong: (max: number) => `Ce champ doit contenir au plus ${max} caractères.`,
    reason: 'Le motif doit contenir entre 5 et 300 caractères.',
    temporaryPassword: 'Le mot de passe temporaire doit contenir entre 10 et 72 octets.',
  },

  me: {
    title: 'Mon espace',
    subtitle: 'Vos informations, votre profil public et les raccourcis de votre compte.',
    identityTitle: 'Compte',
    nameLabel: 'Nom',
    emailLabel: 'E-mail',
    roleLabel: 'Rôle',
    publisherLabel: 'Créateur',
    noPublisher: 'Aucun créateur rattaché',
    publicPage: 'Voir ma page publique',
    studio: 'Profil du studio',
    team: "Gérer l'équipe",
    administration: 'Administration',
    becomeCreatorTitle: 'Ouvrez votre studio',
    becomeCreatorBody:
      'Vous suivez déjà des cours ? Vous pouvez aussi créer votre propre espace et publier vos formations.',
    profileTitle: 'Profil public',
    profileLead:
      'Ces informations apparaissent sur votre page formateur. La biographie est affichée comme du texte, jamais comme du HTML.',
    fullNameLabel: 'Nom complet',
    headlineLabel: 'Titre professionnel',
    headlinePlaceholder: 'Ex. Formatrice en montage vidéo',
    bioLabel: 'Biographie',
    bioPlaceholder: 'Présentez votre parcours, vos méthodes et vos domaines.',
    websiteLabel: 'Site web',
    instagramLabel: 'Instagram',
    linkedinLabel: 'LinkedIn',
    youtubeLabel: 'YouTube',
    linkHint: 'Adresse HTTPS complète. Laissez vide pour masquer le lien.',
    avatarLabel: 'Photo de profil',
    avatarHint: 'Image PNG ou JPEG, 1 Mo maximum.',
    saved: 'Profil enregistré.',
    loadError: 'Impossible de charger votre profil.',
  },

  studio: {
    profile: {
      title: 'Profil du studio',
      subtitle: 'Votre identité publique, vos liens et la couleur de votre espace.',
      readOnlyTitle: 'Profil en lecture seule',
      readOnlyBody:
        'Seul un administrateur du créateur peut modifier ces informations. Vous pouvez les consulter ici.',
      contactNotice:
        "L'API actuelle ne renvoie pas l'e-mail de contact du studio. Saisissez-le à nouveau pour l'enregistrer : il est requis pour éviter de l'effacer.",
      identityTitle: 'Identité',
      nameLabel: 'Nom du créateur',
      headlineLabel: 'Accroche',
      headlinePlaceholder: 'Ex. Apprendre la vidéo par la pratique',
      descriptionLabel: 'Description',
      descriptionPlaceholder: 'Présentez votre studio, votre approche et votre public.',
      linksTitle: 'Liens',
      colorLabel: 'Couleur du studio',
      colorHint: 'Elle colore votre page publique et les boutons de votre studio.',
      logoLabel: 'Logo',
      logoHint: 'Image PNG ou JPEG, 1 Mo maximum.',
      contactLabel: 'E-mail de contact',
      contactHint: 'Requis à l’enregistrement pour conserver la valeur existante.',
      save: 'Enregistrer le profil',
      saved: 'Profil du studio enregistré.',
      publicPage: 'Voir la page publique',
      loadError: 'Impossible de charger le profil du studio.',
      notFound: "Ce studio n'existe pas ou n'est plus actif.",
    },
    team: {
      title: 'Équipe',
      subtitle: 'Ajoutez des formateurs et administrateurs à votre organisation.',
      organizationOnlyTitle: 'Équipe réservée aux organisations',
      organizationOnlyBody:
        "Votre créateur est de type indépendant. Un créateur indépendant enseigne seul et ne peut pas ajouter de membres. Créez une organisation pour gérer une équipe.",
      add: 'Ajouter un membre',
      addTitle: 'Ajouter un membre',
      fullNameLabel: 'Nom complet',
      fullNamePlaceholder: 'Prénom Nom',
      emailLabel: 'E-mail',
      emailPlaceholder: 'formateur@exemple.com',
      roleLabel: 'Rôle',
      passwordLabel: 'Mot de passe temporaire',
      passwordHint:
        '10 à 72 octets. Le membre devra le changer à sa première connexion.',
      addSubmit: 'Ajouter le membre',
      added: 'Membre ajouté. Il devra changer son mot de passe à la première connexion.',
      empty: "L'équipe est vide.",
      columnMember: 'Membre',
      columnRole: 'Rôle',
      columnStatus: 'Statut',
      columnCreated: 'Ajouté le',
      columnActions: 'Actions',
      disable: 'Désactiver',
      enable: 'Réactiver',
      disabled: 'Membre désactivé.',
      enabled: 'Membre réactivé.',
      reset: 'Réinitialiser le mot de passe',
      resetTitle: 'Réinitialiser le mot de passe',
      resetBody: (name: string) =>
        `Définissez un mot de passe temporaire pour ${name}. Il devra le changer à la prochaine connexion.`,
      resetSubmit: 'Réinitialiser',
      resetDone: 'Mot de passe temporaire défini.',
      loadError: "Impossible de charger l'équipe.",
      temporaryBadge: 'Mot de passe temporaire',
      selfBadge: 'Vous',
    },
  },

  publicPublisher: {
    notFoundTitle: 'Créateur introuvable',
    notFoundBody: "Ce créateur n'existe pas ou n'est plus actif.",
    teamTitle: 'Équipe',
    teamLead: 'Les formateurs de cette organisation.',
    linksTitle: 'Retrouver ce créateur',
    memberCta: 'Voir la page de ce formateur',
    individualBadge: 'Créateur indépendant',
    organizationBadge: 'Organisation',
    loadError: 'Impossible de charger cette page.',
  },

  publicInstructor: {
    notFoundTitle: 'Formateur introuvable',
    notFoundBody: "Ce formateur n'existe pas ou n'est plus actif.",
    publisherLabel: 'Enseigne chez',
    linksTitle: 'Retrouver ce formateur',
    bioTitle: 'À propos',
    loadError: 'Impossible de charger cette page.',
  },

  admin: {
    title: 'Administration',
    subtitle: 'Modération des comptes et des créateurs, avec journal des actions.',
    tabs: {
      users: 'Comptes',
      publishers: 'Créateurs',
      audit: "Journal d'audit",
    },
    searchPlaceholder: 'Rechercher par nom ou e-mail',
    searchPublisherPlaceholder: 'Rechercher par nom ou adresse',
    filterActor: 'Acteur',
    filterAction: 'Action',
    loadError: 'Impossible de charger les données.',
    empty: 'Aucun résultat.',
    users: {
      columnName: 'Compte',
      columnRole: 'Rôle',
      columnPublisher: 'Créateur',
      columnStatus: 'Statut',
      columnCreated: 'Créé le',
      columnActions: 'Actions',
      disable: 'Désactiver',
      enable: 'Réactiver',
    },
    publishers: {
      columnName: 'Créateur',
      columnType: 'Type',
      columnStatus: 'Statut',
      columnContact: 'Contact',
      columnMembers: 'Membres actifs',
      columnCreated: 'Créé le',
      columnActions: 'Actions',
      suspend: 'Suspendre',
      reactivate: 'Réactiver',
    },
    audit: {
      columnDate: 'Date',
      columnActor: 'Acteur',
      columnAction: 'Action',
      columnTarget: 'Cible',
      columnReason: 'Motif',
    },
    reasonTitle: (target: string) => `Modifier le statut de ${target}`,
    reasonLabel: 'Motif',
    reasonPlaceholder: 'Expliquez la décision en quelques mots.',
    reasonHelp: 'Entre 5 et 300 caractères. Le motif est conservé dans le journal.',
    reasonTooShort: 'Le motif doit contenir au moins 5 caractères.',
    reasonTooLong: 'Le motif doit contenir au plus 300 caractères.',
    confirm: 'Confirmer',
    cancel: 'Annuler',
    updated: 'Statut mis à jour.',
  },

  privacy: {
    title: 'Politique de confidentialité',
    updated: 'Dernière mise à jour : 14 novembre 2026',
    intro:
      "SANAA est une plateforme d'apprentissage en ligne. Cette page décrit les données traitées, pourquoi elles le sont et comment exercer vos droits.",
    sections: [
      {
        title: 'Responsable du traitement',
        body: "SANAA exploite la plateforme et agit comme responsable du traitement pour les comptes, l'authentification et la sécurité. Chaque créateur reste responsable des contenus de cours qu'il publie et de la relation pédagogique avec ses apprenants.",
      },
      {
        title: 'Données collectées',
        body: "Nom complet, adresse e-mail, mot de passe stocké sous forme de hachage, rôle et rattachement éventuel à un créateur. Les créateurs fournissent aussi le nom de leur studio, une description, des liens publics et un e-mail de contact. Les paiements conservent le montant, le moyen de paiement, le statut et le justificatif transmis.",
      },
      {
        title: 'Finalités',
        body: "Créer et sécuriser votre compte, vous authentifier, afficher les pages publiques des créateurs, traiter les paiements et les accès aux cours, gérer les équipes et répondre aux obligations de modération. Les journaux d'audit servent à protéger les apprenants et à tracer les accès sensibles.",
      },
      {
        title: 'Base légale et durée de conservation',
        body: "Le contrat qui vous lie à SANAA fonde le traitement du compte et des cours. Les obligations légales et la sécurité fondent la conservation des justificatifs et des journaux d'audit. Les données de compte sont conservées tant que le compte existe, puis archivées ou supprimées selon les obligations applicables.",
      },
      {
        title: 'Vos droits',
        body: "Vous pouvez demander l'accès à vos données, leur rectification, leur effacement, la limitation du traitement et la portabilité lorsque la loi le prévoit. Vous pouvez aussi vous opposer à certains traitements. Une demande peut être adressée au créateur concerné pour ses contenus ou à SANAA pour le compte et la plateforme.",
      },
      {
        title: 'Cookies et stockage local',
        body: "Le jeton d'accès reste en mémoire et n'est jamais écrit dans le stockage du navigateur. Le jeton de renouvellement est un cookie HttpOnly, SameSite Strict, limité au chemin d'authentification. Le navigateur conserve uniquement votre préférence de thème clair ou sombre.",
      },
      {
        title: 'Sécurité et sous-traitants',
        body: "Les accès sont limités par rôle et par créateur. Les mots de passe sont hachés, les jetons sont signés et les actions d'administration sont auditées. L'hébergement, l'envoi des e-mails et le traitement des paiements peuvent faire appel à des prestataires qui n'accèdent aux données que pour la durée de leur mission.",
      },
      {
        title: 'Contact',
        body: "Pour toute question sur cette politique ou pour exercer vos droits, contactez l'équipe SANAA depuis votre espace personnel. Nous répondons dans les délais prévus par la réglementation applicable.",
      },
    ],
  },

  status: {
    DRAFT: 'Brouillon',
    PUBLISHED: 'Publié',
    ARCHIVED: 'Archivé',
  } satisfies Record<CourseStatus, string>,

  catalogue: {
    title: 'Catalogue',
    subtitle: 'Découvrez les cours disponibles sur la plateforme.',
    empty: "Aucun cours n'est disponible pour le moment.",
    free: 'Gratuit',
    loadErrorTitle: 'Impossible de charger le catalogue',
    loadErrorBody: 'Vérifiez votre connexion puis réessayez.',
  },

  courseDetail: {
    back: 'Retour au catalogue',
    curriculum: 'Programme',
    curriculumEmpty: "Le programme n'est pas encore disponible.",
    lessonEmpty: 'Aucune leçon pour le moment.',
    freePreview: 'Aperçu gratuit',
    lessonCount: (count: number) => `${count} leçon${count > 1 ? 's' : ''}`,
    priceLabel: 'Prix',
    statusLabel: 'Statut',
    buy: 'Acheter ce cours',
    enrolled: 'Déjà inscrit',
    loadErrorTitle: 'Impossible de charger ce cours',
    loadErrorBody: 'Vérifiez votre connexion puis réessayez.',
    notFoundTitle: 'Cours introuvable',
    notFoundBody: "Ce cours n'existe pas ou n'est plus accessible.",
  },

  myCourses: {
    title: 'Mes cours',
    subtitle: 'Retrouvez les formations auxquelles vous êtes inscrit.',
    empty: "Vous n'êtes inscrit à aucun cours pour le moment.",
    browse: 'Parcourir le catalogue',
    refresh: 'Actualiser',
    enrolledAt: (date: string) => `Inscrit le ${date}`,
    loadErrorTitle: 'Impossible de charger vos cours',
    loadErrorBody: 'Vérifiez votre connexion puis réessayez.',
  },

  payments: {
    enrollmentDelay: "L'accès apparaît quelques secondes après la validation.",
    pendingTag: 'Paiement en attente',
    scrollHint: 'Faites défiler le tableau horizontalement pour voir toutes les colonnes.',
    methods: {
      BANK_WIRE: 'Virement bancaire',
      CASHPLUS: 'Dépôt CashPlus',
    } satisfies Record<PaymentMethod, string>,
    methodInstructions: {
      BANK_WIRE:
        'Effectuez un virement du montant indiqué vers le compte communiqué par votre créateur, puis joignez le reçu. Indiquez votre e-mail en référence.',
      CASHPLUS:
        'Déposez le montant indiqué dans une agence CashPlus, puis joignez le reçu. Indiquez votre e-mail en référence.',
    } satisfies Record<PaymentMethod, string>,
    statuses: {
      PENDING_REVIEW: 'En attente',
      APPROVED: 'Approuvé',
      REJECTED: 'Refusé',
    } satisfies Record<PaymentStatus, string>,
    buy: {
      title: 'Acheter ce cours',
      courseLabel: 'Cours',
      priceLabel: 'Montant à régler',
      methodLabel: 'Moyen de paiement',
      receiptLabel: 'Justificatif de paiement',
      acceptedFormats: 'Image JPEG ou PNG, ou PDF. 5 Mo maximum.',
      chooseFile: 'Choisir un fichier',
      removeFile: 'Retirer',
      submit: 'Envoyer le paiement',
      submitting: 'Envoi…',
      success: 'Paiement envoyé, en attente de validation',
      cancel: 'Annuler',
    },
    errors: {
      conflict: 'Un paiement est déjà en cours ou vous êtes déjà inscrit à ce cours.',
      tooLarge: 'Le fichier dépasse 5 Mo.',
      unrecognized: 'Fichier non reconnu : envoyez une image JPEG, PNG ou un PDF.',
      unavailable: 'Le service de paiement est momentanément indisponible. Réessayez plus tard.',
      emptyFile: 'Sélectionnez un justificatif non vide.',
    },
    myPayments: {
      title: 'Mes paiements',
      subtitle: 'Suivez vos paiements et consultez vos justificatifs.',
      empty: "Vous n'avez encore envoyé aucun paiement.",
      browse: 'Parcourir le catalogue',
      loadErrorTitle: 'Impossible de charger vos paiements',
      loadErrorBody: 'Vérifiez votre connexion puis réessayez.',
      columnDate: 'Date',
      columnCourse: 'Cours',
      columnAmount: 'Montant',
      columnMethod: 'Moyen de paiement',
      columnStatus: 'Statut',
      columnActions: 'Actions',
      viewReceipt: 'Voir le justificatif',
      reason: 'Motif',
      retry: 'Réessayer',
    },
    admin: {
      title: 'Paiements',
      subtitle: 'Validez ou refusez les justificatifs envoyés par les apprenants.',
      filterLabel: 'Statut',
      filterAll: 'Tous',
      empty: 'Aucun paiement ne correspond à ce filtre.',
      loadErrorTitle: 'Impossible de charger les paiements',
      loadErrorBody: 'Vérifiez votre connexion puis réessayez.',
      columnStudent: 'Apprenant',
      columnCourse: 'Cours',
      columnAmount: 'Montant',
      columnMethod: 'Moyen de paiement',
      columnDate: 'Date',
      columnStatus: 'Statut',
      columnActions: 'Actions',
      viewReceipt: 'Voir le justificatif',
      approve: 'Approuver',
      approveConfirm: "Approuver ce paiement et ouvrir l'accès au cours ?",
      approved: 'Paiement approuvé.',
      reject: 'Refuser',
      rejected: 'Paiement refusé.',
      alreadyProcessed: 'Ce paiement a déjà été traité.',
      rejectTitle: 'Refuser le paiement',
      rejectIntro: (student: string, course: string) =>
        `Indiquez à ${student} pourquoi le justificatif du cours « ${course} » est refusé.`,
      rejectLabel: 'Motif du refus',
      rejectPlaceholder: 'Ex. Le montant du justificatif ne correspond pas au prix du cours.',
      rejectTooShort: 'Le motif doit contenir au moins 3 caractères.',
      rejectTooLong: 'Le motif doit contenir au plus 500 caractères.',
      rejectCounter: (length: number) => `${length} / 500 caractères`,
      confirmReject: 'Refuser le paiement',
      cancel: 'Annuler',
    },
  },

  manageCourses: {
    title: 'Mes formations',
    subtitle: 'Créez, modifiez et publiez les cours de votre créateur.',
    adminSubtitle: 'Tous les cours de votre créateur.',
    create: 'Créer un cours',
    empty: 'Aucun cours pour le moment. Créez votre premier cours.',
    loadErrorTitle: 'Impossible de charger vos formations',
    loadErrorBody: 'Vérifiez votre connexion puis réessayez.',
    columnTitle: 'Cours',
    columnStatus: 'Statut',
    columnPrice: 'Prix',
    columnLessons: 'Leçons',
    columnActions: 'Actions',
    edit: 'Modifier',
    open: 'Ouvrir',
  },

  editor: {
    createTitle: 'Créer un cours',
    editTitle: 'Modifier le cours',
    titleLabel: 'Titre',
    titlePlaceholder: 'Ex. Montage vidéo : les bases',
    descriptionLabel: 'Description',
    descriptionPlaceholder: 'Présentez le contenu et les objectifs du cours.',
    priceLabel: 'Prix',
    currencyLabel: 'Devise',
    currencyValue: 'MAD',
    save: 'Enregistrer',
    saving: 'Enregistrement…',
    create: 'Créer le cours',
    creating: 'Création…',
    cancel: 'Annuler',
    created: 'Cours créé.',
    saved: 'Cours enregistré.',
    archivedNotice: 'Ce cours est archivé : il ne peut plus être modifié.',
    curriculumTitle: 'Programme',
    curriculumEmpty: 'Ajoutez un premier module pour construire le programme.',
    addModule: 'Ajouter un module',
    moduleModalTitle: 'Ajouter un module',
    moduleTitleLabel: 'Titre du module',
    moduleAdded: 'Module ajouté.',
    addLesson: 'Ajouter une leçon',
    lessonModalTitle: 'Ajouter une leçon',
    lessonTitleLabel: 'Titre de la leçon',
    durationLabel: 'Durée (secondes)',
    freePreviewLabel: 'Aperçu gratuit',
    lessonAdded: 'Leçon ajoutée.',
    publish: 'Publier',
    publishConfirm: 'Publier ce cours ?',
    archive: 'Archiver',
    archiveConfirm: 'Archiver ce cours ? Cette action est définitive.',
    published: 'Cours publié.',
    archived: 'Cours archivé.',
    confirm: 'Confirmer',
    statusLabel: 'Statut',
    versionLabel: 'Version',
    conflict: 'Ce cours a été modifié ailleurs. Rechargez la page.',
    publishNeedsLesson: 'Ajoutez au moins une leçon avant de publier.',
    loadErrorTitle: 'Impossible de charger le cours',
    loadErrorBody: 'Vérifiez votre connexion puis réessayez.',
    notFoundTitle: 'Cours introuvable',
    notFoundBody: "Ce cours n'existe pas ou n'est plus accessible.",
    validation: {
      titleLength: 'Le titre doit contenir entre 3 et 120 caractères',
      descriptionMax: 'La description doit contenir au plus 4000 caractères',
      priceRequired: 'Le prix est requis',
      priceRange: 'Le prix doit être compris entre 0 et 1 000 000',
      priceDecimals: 'Le prix doit comporter au plus 2 décimales',
      moduleTitleRequired: 'Le titre du module est requis',
      moduleTitleMax: 'Le titre du module doit contenir au plus 120 caractères',
      lessonTitleRequired: 'Le titre de la leçon est requis',
      lessonTitleMax: 'Le titre de la leçon doit contenir au plus 120 caractères',
      durationRequired: 'La durée est requise',
      durationMin: 'La durée doit être positive ou nulle',
    },
  },

  forbidden: {
    title: 'Accès refusé',
    body: "Vous n'avez pas les droits nécessaires pour consulter cette page.",
    backHome: "Retour à l'accueil",
  },

  notFound: {
    title: 'Page introuvable',
    body: "L'adresse demandée n'existe pas ou a été déplacée.",
    backHome: "Retour à l'accueil",
  },

  errors: {
    invalidData: 'Données invalides. Vérifiez les champs signalés.',
    invalidCredentials: 'Identifiants incorrects.',
    sessionExpired: 'Session expirée, veuillez vous reconnecter.',
    forbidden: "Vous n'avez pas les droits nécessaires pour cette action.",
    notFound: 'Introuvable.',
    emailAlreadyRegistered: 'Un compte existe déjà avec cet e-mail.',
    slugTaken: 'Cette adresse est déjà prise.',
    membershipExists: 'Ce compte est déjà rattaché à un créateur.',
    teamEmailExists: 'Un compte existe déjà avec cet e-mail.',
    teamAlreadyMember: 'Ce compte est déjà rattaché à un créateur.',
    teamSelfDisable: 'Vous ne pouvez pas désactiver votre propre compte.',
    teamLastAdmin: "Le créateur doit conserver au moins un administrateur actif.",
    teamOrganizationOnly: "Seule une organisation peut gérer une équipe.",
    conflict: 'Cette action entre en conflit avec une donnée existante.',
    tooMany: 'Trop de tentatives, réessayez dans quelques secondes.',
    server: 'Erreur du serveur, réessayez plus tard.',
    network: 'Impossible de joindre le serveur. Vérifiez votre connexion puis réessayez.',
    unknown: 'Une erreur est survenue, réessayez plus tard.',
    wrongCurrentPassword: 'Le mot de passe actuel est incorrect.',
  },
} as const;

/**
 * Maps an HTTP status to a French message. The raw English detail of an RFC
 * 7807 response is never shown directly to the user.
 */
export function messageForStatus(status: number, context: ErrorContext = 'default'): string {
  if (status === 401) {
    return context === 'login' ? fr.errors.invalidCredentials : fr.errors.sessionExpired;
  }
  switch (status) {
    case 400:
      return fr.errors.invalidData;
    case 403:
      return fr.errors.forbidden;
    case 404:
      return fr.errors.notFound;
    case 409:
      return fr.errors.conflict;
    case 429:
      return fr.errors.tooMany;
    case 0:
      return fr.errors.network;
    default:
      return status >= 500 ? fr.errors.server : fr.errors.unknown;
  }
}
