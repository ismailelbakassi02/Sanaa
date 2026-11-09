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
