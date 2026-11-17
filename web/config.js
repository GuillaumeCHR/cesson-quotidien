// Réglages de déploiement. Le workflow « Version web » remplace ce fichier au déploiement : la clé d'API STAR
// (gratuite, données publiques, la même que celle de l'app Android) vient d'un secret GitHub et n'est jamais versionnée.
// Sans clé, les appels à STAR sont anonymes et partagent le quota commun du portail, parfois épuisé le soir.
export const STAR_CLE = "";
