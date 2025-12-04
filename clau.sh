#!/bin/bash

# Script de lancement Claude Code avec auto-confirmation
# Usage: ./launch_claude.sh [répertoire_optionnel]

# Couleurs pour l'affichage
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Fonction pour vérifier si Claude Code est installé
check_claude() {
    if ! command -v claude >/dev/null 2>&1; then
        echo "❌ Claude Code n'est pas installé ou pas dans le PATH"
        echo "💡 Installez-le d'abord avec le script d'installation"
        exit 1
    fi
}

# Fonction pour aller dans un répertoire spécifique
change_directory() {
    if [ -n "$1" ]; then
        if [ -d "$1" ]; then
            print_info "Changement vers le répertoire: $1"
            cd "$1" || exit 1
        else
            print_warning "Le répertoire '$1' n'existe pas"
            print_info "Création du répertoire..."
            mkdir -p "$1"
            cd "$1" || exit 1
            print_success "Répertoire créé et ouvert: $1"
        fi
    else
        print_info "Répertoire actuel: $(pwd)"
    fi
}

# Fonction principale
main() {
    echo "🤖 Lancement de Claude Code avec auto-confirmation..."
    echo ""

    # Vérifier que Claude est installé
    check_claude

    # Changer de répertoire si spécifié
    change_directory "$1"

    print_success "Lancement de Claude Code avec auto-permissions"
    print_warning "⚠️  Claude pourra exécuter des commandes automatiquement sans demander confirmation"
    print_info "Répertoire de travail: $(pwd)"
    echo ""
    echo "🚀 Démarrage de Claude Code..."
    echo "=========================================="

    # Lancer Claude Code avec skip des permissions (équivalent de -y)
    claude --dangerously-skip-permissions
}

# Aide
show_help() {
    echo "Usage: $0 [répertoire]"
    echo ""
    echo "Options:"
    echo "  répertoire    Répertoire de travail (optionnel)"
    echo "  -h, --help    Afficher cette aide"
    echo ""
    echo "Exemples:"
    echo "  $0                    # Lance Claude dans le répertoire actuel"
    echo "  $0 /root/mon-projet   # Lance Claude dans /root/mon-projet"
    echo "  $0 ~/test-claude      # Lance Claude dans ~/test-claude"
}

# Gestion des arguments
case "$1" in
    -h|--help)
        show_help
        exit 0
        ;;
    *)
        main "$1"
        ;;
esac
