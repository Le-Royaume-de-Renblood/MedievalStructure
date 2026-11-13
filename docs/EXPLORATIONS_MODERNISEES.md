# Explorations — refonte du 13 novembre 2026

Medieval Structures **0.1.52**, protocole réseau **3**. Compatible avec Coins **1.0.75** et NPC Shopkeeper **1.0.59** du paquet. Mettre à jour Structures côté serveur **et** clients ; ne pas mélanger les anciens clients de protocole 2. Aucun changement du front, du backend ou de leurs bases.

## Crash retrouvé et corrigé

Le crash local `crash-2026-11-12_19.55.54-server.txt` indique un arrêt du watchdog après un tick de 60 secondes. La pile remonte à `ExplorationManager.findSafeCandidate` (ancien Structures 0.1.49), qui appelle un chargement bloquant de chunk sur le thread serveur. Le correctif supprime cet appel. La présence de Canary/Connector dans le pack ne suffit pas à leur attribuer la cause.

La préparation utilise désormais des tickets de chunks et vérifie leur disponibilité aux ticks suivants. **Aucun `getChunk`, `Future.get/join` ou attente active pour préparer le terrain.** Attention : l'API Minecraft `getChunkFuture` peut elle-même attendre si elle est appelée sur le thread serveur ; elle n'est pas utilisée ici.

Une demande de ticket au maximum par étape de préparation, zone de camp 3×3 chunks, 24 candidats maximum, délai borné et libération des tickets à l'annulation/échec. En cas d'échec : état explicite et annulation/remboursement depuis le hall, sans relancer un débit. Les morceaux de camp déjà créés sont remis dans la file de restauration.

La construction de la base est étalée à 24 modifications maximum et environ 2 ms de travail par passage. Le nettoyage garde une limite de 48 blocs et environ 2 ms par passage. Ce sont des budgets de boucle : un appel interne d'un autre mod peut toujours durer plus longtemps ; il n'est pas possible de garantir 20 TPS pour tout le pack à partir de ces seuls tests.

## Parcours et correctifs

- Hall plus lisible : préparation, équipe, talents, suivi du rassemblement/terrain/camp/exploration/retour, aide repliable et confirmation d'annulation.
- Même transformation d'échelle pour dessin, widgets, clics, glissements et infobulles ; panneau logique 560×380 adapté aux petites GUI, au lieu d'imposer un panneau plus grand que l'écran.
- Rafraîchissement de statut en jeu une fois par seconde, sans HTTP ; recherche des coffres cinq secondes seulement dans les écrans concernés, au lieu d'une fois par seconde dans chaque rendu.
- Annulation disponible après le paiement pendant la préparation. Seul le payeur peut annuler le départ payé. Effacer une préparation non payée ne déclenche pas de remboursement.
- Les nouveaux départs comptent les UUID invités, pas les passants/spectateurs. Dix participants maximum, pas de doublons ; pas d'édition d'un hall déjà en départ/exploration. Les préparations héritées sans liste d'UUID gardent la compatibilité historique.
- La formule de base à coût zéro déjà présente dans le barème fonctionne désormais : aucun faux paiement bancaire à zéro, et aucun remboursement fictif. Aucun barème de prix n'est changé.
- Retour du camp, retour après mort/reconnexion et arrivée staff préparent les chunks sans attente bloquante. La position de réapparition est sauvegardée avant de poser les lits du camp et restaurée au retour pour les nouvelles expéditions.
- Les modifications de terrain d'un nouveau camp sont conservées pour restaurer l'état initial, au lieu de remplacer systématiquement les sols par de l'air. Annulation partielle et états de préparation sont sauvegardables/reprenables. L'ancien état d'un terrain déjà modifié par une version historique ne peut pas être reconstitué rétroactivement.
- Coffres : copie avec propriétés/NBT conservées ; si le coffre du hall est occupé, celui du camp est conservé et signalé au staff, sans écraser le contenu du hall. Aucun bloc-entité détecté n'est écrasé pour créer un emplacement de coffre.
- Animaux : un transfert refusé ou une création impossible laisse l'animal au camp et diffère la fin, au lieu de supprimer l'original sans accueil confirmé. Le cas d'interaction avec les événements d'autres mods reste à vérifier sur le pack réel.
- États d'invitation/préparation inconnus dans une sauvegarde ancienne : chargement tolérant, informations financières conservées, pas de crash sur un enum illisible.
- Son de compte à rebours envoyé une fois au point de départ, au lieu d'un broadcast répété pour chaque joueur proche.

Les halls hors Overworld sont refusés clairement : le parcours existant supposait déjà un départ/retour Overworld et pouvait auparavant employer le mauvais monde.

## Commandes

```text
/ms exploration tuto
/ms exploration status
/ms exploration retour
```

`status` est réservé à l'administration : départs, expéditions, arrivées préparées, nettoyage et coffres retenus. `retour` permet au joueur présent en exploration de préparer son propre retour au hall ; cela ne termine pas immédiatement l'expédition du groupe et ne transfère pas ses coffres avant la fin normale. Il ne s'agit pas d'un remboursement de voyage.

Les commandes existantes de coffres retenus et de debug sont conservées. Ne pas activer le debug sur une séance normale : il réduit la durée pour les essais. L'arrivée staff annonce maintenant une préparation de téléportation et non une arrivée déjà confirmée.

## Validation réalisée

Huit tests Forge dans un monde jetable : ticket non bloquant et libération, délai/échec, transformations de GUI, état historique et reçu, paquet de suivi, puis trois cycles distants réels :

1. Préparation/construction, transfert d'un coffre avec sept diamants nommés, retour et restauration.
2. Coffre du hall rempli entre départ et retour : son contenu reste intact et le coffre du camp/NBT reste récupérable.
3. Annulation pendant la construction : coffre source non déplacé et terrain restauré.

Le trio exact est aussi essayé sur un dédié Forge 47.4.3 isolé. Ces tests ne lancent pas le monde personnel de préprod, ne se connectent pas à une base réelle et ne constituent pas une mesure de charge du pack complet. Le rendu client, la banque réelle, les invitations à deux comptes, les lits et animaux sont à contrôler manuellement.

## Recette à faire après mise à jour

1. Arrêter proprement le serveur et les clients, sauvegarder le monde/configs, remplacer le trio (une seule version de chaque mod). Le serveur local de recette et Test Mods RP reçoivent les JAR sauvegardés par cette livraison ; la production reste à mettre à jour par le propriétaire.
2. Ouvrir le hall aux échelles GUI habituelles puis grande GUI. Vérifier les clics, participants 7–10, détails, talents, aide et confirmation d'annulation.
3. Préparer une formule gratuite, puis une payante. Vérifier tarif, banque et reçu. Annuler un départ payé pendant la préparation : un seul remboursement ; ne pas retenter en créant un nouveau paiement si le reçu reste incertain.
4. Avec A et B invités, laisser un passant C près du hall : il ne doit ni remplacer B ni voyager. Déplacer B hors de la zone pendant la préparation, puis le ramener.
5. Lancer vers du terrain neuf en surveillant la console et `/forge tps`. La préparation doit avancer sans geler la console ; un échec doit rester annulable.
6. Partir avec un coffre contenant un objet nommé/enchanté. Au retour normal, vérifier quantité et propriétés. Refaire en remplissant le coffre d'origine : celui du camp doit être retenu, sans écrasement.
7. Tester déconnexion/reconnexion avant le départ, en préparation et dans le camp ; redémarrer une copie du serveur pendant la préparation. Vérifier reprise/annulation sans nouveau débit et état cohérent des coffres.
8. Avec et sans talent de lit, mourir/revenir ; finir l'expédition et vérifier que le lit temporaire ne remplace pas durablement l'ancienne réapparition.
9. Capturer des animaux en respectant la capacité et les laisses. Vérifier l'enclos, le retour, noms/équipement/propriétaire et absence de suppression si le pack refuse un spawn.
10. Essayer `/ms exploration retour` puis `status`, et vérifier le nettoyage après la fin. Les anciens camps peuvent avoir des limites de restauration documentées ci-dessus.

En cas de problème, conserver le premier reçu et noter heure, joueur, hall, phase affichée, quantité et log ; éviter de supprimer le camp ou ses coffres pour débloquer un retour.
