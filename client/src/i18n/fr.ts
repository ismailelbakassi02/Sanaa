import type { CourseStatus } from '../api/courses';
import type { PaymentMethod, PaymentStatus } from '../api/payments';
import type { Role } from '../api/types';

/**
 * Every user-visible string of the application lives here. Components never
 * hardcode French text.
 */
export type ErrorContext = 'login' | 'register' | 'default';

export const fr = {
  documentTitle: (tenantName: string) => `${tenantName} · SANAA`,

  app: {
    name: 'SANAA',
    tagline: "Apprenez à votre rythme, dans l'espace de votre organisation.",
    panelCaption: 'Cours en ligne, paiements et suivi réunis dans un seul espace.',
  },

  common: {
    loading: 'Chargement…',
    retry: 'Réessayer',
    required: 'Ce champ est requis',
    emailInvalid: 'Adresse e-mail invalide',
    passwordMin: 'Le mot de passe doit contenir au moins 10 caractères',
    tenant: 'Organisation',
    soon: 'Bientôt disponible',
  },

  theme: {
    toDark: 'Passer en mode sombre',
    toLight: 'Passer en mode clair',
  },

  auth: {
    login: {
      title: 'Connexion',
      subtitle: "Accédez à l'espace de formation de votre organisation.",
      emailLabel: 'E-mail',
      emailPlaceholder: 'vous@exemple.com',
      passwordLabel: 'Mot de passe',
      passwordPlaceholder: 'Votre mot de passe',
      submit: 'Se connecter',
      submitting: 'Connexion…',
      noAccount: 'Pas encore de compte ?',
      createAccount: 'Créer un compte',
      registrationDisabled:
        "La création de compte n'est pas disponible pour cette organisation. Contactez votre administrateur.",
    },
    register: {
      title: 'Créer un compte',
      subtitle: 'Rejoignez votre organisation et commencez votre formation.',
      nameLabel: 'Nom complet',
      namePlaceholder: 'Prénom Nom',
      emailLabel: 'E-mail',
      emailPlaceholder: 'vous@exemple.com',
      passwordLabel: 'Mot de passe',
      passwordPlaceholder: '10 caractères minimum',
      confirmLabel: 'Confirmer le mot de passe',
      confirmPlaceholder: 'Saisissez à nouveau le mot de passe',
      submit: 'Créer mon compte',
      submitting: 'Création…',
      haveAccount: 'Vous avez déjà un compte ?',
      signIn: 'Se connecter',
      passwordMismatch: 'Les mots de passe ne correspondent pas',
      success: 'Compte créé, bienvenue !',
    },
  },

  tenantPicker: {
    title: 'Choisissez votre organisation',
    subtitle:
      "Saisissez l'identifiant de votre organisation pour accéder à son espace de formation.",
    slugLabel: "Identifiant de l'organisation",
    slugPlaceholder: 'atlas',
    submit: 'Continuer',
    invalidSlug:
      'Identifiant invalide : 3 à 63 caractères, lettres minuscules, chiffres ou tirets.',
    documentTitle: 'Choisissez votre organisation · SANAA',
  },

  brands: {
    loading: 'Chargement de votre organisation…',
    notFoundTitle: 'Organisation introuvable',
    notFoundBody: (slug: string) =>
      `Aucune organisation ne correspond à « ${slug} ». Vérifiez l'adresse utilisée.`,
    notFoundDocumentTitle: 'Organisation introuvable · SANAA',
    chooseTenant: 'Choisir une organisation',
    unavailableTitle: 'Impossible de contacter le serveur',
    unavailableBody: 'Vérifiez que le serveur est démarré puis réessayez.',
    genericTitle: "Impossible de charger l'organisation",
    genericBody:
      "Une erreur est survenue lors du chargement de l'organisation. Réessayez plus tard.",
  },

  home: {
    greeting: (name: string) => `Bonjour, ${name}`,
    sessionSince: (date: string) => `Session ouverte le ${date}`,
    profileTitle: 'Votre profil',
    nameLabel: 'Nom',
    emailLabel: 'E-mail',
    roleLabel: 'Rôle',
    tenantLabel: 'Organisation',
    spaceTitle: 'Votre espace',
    spaceSubtitle: "Cette section sera disponible dans une prochaine version.",
    loadErrorTitle: 'Impossible de charger votre profil',
    loadErrorBody: 'Vérifiez votre connexion puis réessayez.',
    refresh: 'Actualiser',
  },

  status: {
    DRAFT: 'Brouillon',
    PUBLISHED: 'Publié',
    ARCHIVED: 'Archivé',
  } satisfies Record<CourseStatus, string>,

  catalogue: {
    title: 'Catalogue',
    subtitle: 'Découvrez les cours disponibles dans votre organisation.',
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
    scrollHint:
      'Faites défiler le tableau horizontalement pour voir toutes les colonnes.',
    methods: {
      BANK_WIRE: 'Virement bancaire',
      CASHPLUS: 'Dépôt CashPlus',
    } satisfies Record<PaymentMethod, string>,
    methodInstructions: {
      BANK_WIRE:
        'Effectuez un virement du montant indiqué vers le compte communiqué par votre organisation, puis joignez le reçu. Indiquez votre e-mail en référence.',
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
      conflict:
        'Un paiement est déjà en cours ou vous êtes déjà inscrit à ce cours.',
      tooLarge: 'Le fichier dépasse 5 Mo.',
      unrecognized:
        'Fichier non reconnu : envoyez une image JPEG, PNG ou un PDF.',
      unavailable:
        'Le service de paiement est momentanément indisponible. Réessayez plus tard.',
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
      subtitle:
        'Validez ou refusez les justificatifs envoyés par les étudiants.',
      filterLabel: 'Statut',
      filterAll: 'Tous',
      empty: 'Aucun paiement ne correspond à ce filtre.',
      loadErrorTitle: 'Impossible de charger les paiements',
      loadErrorBody: 'Vérifiez votre connexion puis réessayez.',
      columnStudent: 'Étudiant',
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
      rejectPlaceholder:
        'Ex. Le montant du justificatif ne correspond pas au prix du cours.',
      rejectTooShort: 'Le motif doit contenir au moins 3 caractères.',
      rejectTooLong: 'Le motif doit contenir au plus 500 caractères.',
      rejectCounter: (length: number) => `${length} / 500 caractères`,
      confirmReject: 'Refuser le paiement',
      cancel: 'Annuler',
    },
  },

  manageCourses: {
    title: 'Mes formations',
    subtitle: 'Créez, modifiez et publiez les cours de votre organisation.',
    adminSubtitle: 'Tous les cours de votre organisation.',
    create: 'Créer un cours',
    empty: "Aucun cours pour le moment. Créez votre premier cours.",
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

  roleCards: {
    STUDENT: {
      title: 'Mes cours',
      description:
        "Vos cours, vos inscriptions et la reprise des leçons vidéo apparaîtront ici.",
    },
    INSTRUCTOR: {
      title: 'Éditeur de cours',
      description:
        'Vous créerez ici vos cours, modules et leçons, puis vous publierez les vidéos pour vos étudiants.',
    },
    TENANT_ADMIN: {
      title: 'Paiements et utilisateurs',
      description:
        'Vous validerez les justificatifs de paiement et gérerez les comptes de votre organisation.',
    },
    SUPER_ADMIN: {
      title: 'Organisations',
      description:
        'Vous administrerez les organisations de la plateforme, leur statut et leur identité visuelle.',
    },
  } satisfies Record<Role, { title: string; description: string }>,

  roles: {
    STUDENT: 'Étudiant(e)',
    INSTRUCTOR: 'Formateur',
    TENANT_ADMIN: "Administrateur de l'organisation",
    SUPER_ADMIN: 'Super administrateur',
  } satisfies Record<Role, string>,

  menu: {
    userMenu: 'Menu utilisateur',
    account: 'Compte',
    home: 'Accueil',
    logout: 'Se déconnecter',
    primary: 'Navigation principale',
    catalogue: 'Catalogue',
    myCourses: 'Mes cours',
    myFormations: 'Mes formations',
    payments: 'Paiements',
    myPayments: 'Mes paiements',
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
    invalidData: 'Données invalides',
    invalidCredentials: 'Identifiants incorrects',
    sessionExpired: 'Session expirée, veuillez vous reconnecter',
    forbidden: 'Accès refusé',
    notFound: 'Introuvable',
    alreadyExists: 'Un compte existe déjà avec cet e-mail',
    tooMany: 'Trop de tentatives, réessayez dans quelques secondes',
    server: 'Erreur du serveur, réessayez plus tard',
    network: 'Impossible de joindre le serveur. Vérifiez votre connexion puis réessayez.',
    unknown: 'Une erreur est survenue, réessayez plus tard',
  },
} as const;

/**
 * Maps an HTTP status to a French message. The raw English "detail" of an RFC
 * 7807 response is never shown to the user.
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
      return fr.errors.alreadyExists;
    case 429:
      return fr.errors.tooMany;
    case 0:
      return fr.errors.network;
    default:
      return status >= 500 ? fr.errors.server : fr.errors.unknown;
  }
}
