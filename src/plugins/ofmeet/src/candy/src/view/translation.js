/** File: translation.js
 * Candy - Chats are not dead yet.
 *
 * Authors:
 *   - Patrick Stadler <patrick.stadler@gmail.com>
 *   - Michael Weibel <michael.weibel@gmail.com>
 *
 * Copyright:
 *   (c) 2011 Amiado Group AG. All rights reserved.
 *   (c) 2012-2014 Patrick Stadler & Michael Weibel. All rights reserved.
 */
'use strict';

/* global Candy */

/** Class: Candy.View.Translation
 * Contains translations
 */
Candy.View.Translation = {
	'en' : {
		'status': 'Status: %s',
		'statusConnecting': 'Connecting...',
		'statusConnected' : 'Connected',
		'statusDisconnecting': 'Disconnecting...',
		'statusDisconnected' : 'Disconnected',
		'statusAuthfail': 'Authentication failed',

		'roomSubject'  : 'Subject:',
		'messageSubmit': 'Send',

		'labelUsername': 'Username:',
		'labelNickname': 'Nickname:',
		'labelPassword': 'Password:',
		'loginSubmit'  : 'Login',
		'loginInvalid'  : 'Invalid JID',

		'reason'				: 'Reason:',
		'subject'				: 'Subject:',
		'reasonWas'				: 'Reason was: %s.',
		'kickActionLabel'		: 'Kick',
		'youHaveBeenKickedBy'   : 'You have been kicked from %2$s by %1$s',
		'youHaveBeenKicked'     : 'You have been kicked from %s',
		'banActionLabel'		: 'Ban',
		'youHaveBeenBannedBy'   : 'You have been banned from %1$s by %2$s',
		'youHaveBeenBanned'     : 'You have been banned from %s',

		'privateActionLabel' : 'Private chat',
		'ignoreActionLabel'  : 'Ignore',
		'unignoreActionLabel' : 'Unignore',

		'setSubjectActionLabel': 'Change Subject',

		'administratorMessageSubject' : 'Administrator',

		'userJoinedRoom'           : '%s joined the room.',
		'userLeftRoom'             : '%s left the room.',
		'userHasBeenKickedFromRoom': '%s has been kicked from the room.',
		'userHasBeenBannedFromRoom': '%s has been banned from the room.',
		'userChangedNick': '%1$s has changed his nickname to %2$s.',

		'presenceUnknownWarningSubject': 'Notice:',
		'presenceUnknownWarning'       : 'This user might be offline. We can\'t track his presence.',

		'dateFormat': 'dd.mm.yyyy',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole'			: 'Moderator',
		'tooltipIgnored'		: 'You ignore this user',
		'tooltipEmoticons'		: 'Emoticons',
		'tooltipSound'			: 'Play sound for new private messages',
		'tooltipAutoscroll'		: 'Autoscroll',
		'tooltipStatusmessage'	: 'Display status messages',
		'tooltipAdministration'	: 'Room Administration',
		'tooltipUsercount'		: 'Room Occupants',

		'enterRoomPassword' : 'Room "%s" is password protected.',
		'enterRoomPasswordSubmit' : 'Join room',
		'passwordEnteredInvalid' : 'Invalid password for room "%s".',

		'nicknameConflict': 'Username already in use. Please choose another one.',

		'errorMembersOnly': 'You can\'t join room "%s": Insufficient rights.',
		'errorMaxOccupantsReached': 'You can\'t join room "%s": Too many occupants.',
		'errorAutojoinMissing': 'No autojoin parameter set in configuration. Please set one to continue.',

		'antiSpamMessage' : 'Please do not spam. You have been blocked for a short-time.'
	},
	'de' : {
		'status': 'Status: %s',
		'statusConnecting': 'Verbinden...',
		'statusConnected' : 'Verbunden',
		'statusDisconnecting': 'Verbindung trennen...',
		'statusDisconnected' : 'Verbindung getrennt',
		'statusAuthfail': 'Authentifizierung fehlgeschlagen',

		'roomSubject'  : 'Thema:',
		'messageSubmit': 'Senden',

		'labelUsername': 'Benutzername:',
		'labelNickname': 'Spitzname:',
		'labelPassword': 'Passwort:',
		'loginSubmit'  : 'Anmelden',
		'loginInvalid'  : 'Ungültige JID',

		'reason'				: 'Begründung:',
		'subject'				: 'Titel:',
		'reasonWas'				: 'Begründung: %s.',
		'kickActionLabel'		: 'Kick',
		'youHaveBeenKickedBy'   : 'Du wurdest soeben aus dem Raum %1$s gekickt (%2$s)',
		'youHaveBeenKicked'     : 'Du wurdest soeben aus dem Raum %s gekickt',
		'banActionLabel'		: 'Ban',
		'youHaveBeenBannedBy'   : 'Du wurdest soeben aus dem Raum %1$s verbannt (%2$s)',
		'youHaveBeenBanned'     : 'Du wurdest soeben aus dem Raum %s verbannt',

		'privateActionLabel' : 'Privater Chat',
		'ignoreActionLabel'  : 'Ignorieren',
		'unignoreActionLabel' : 'Nicht mehr ignorieren',

		'setSubjectActionLabel': 'Thema ändern',

		'administratorMessageSubject' : 'Administrator',

		'userJoinedRoom'           : '%s hat soeben den Raum betreten.',
		'userLeftRoom'             : '%s hat soeben den Raum verlassen.',
		'userHasBeenKickedFromRoom': '%s ist aus dem Raum gekickt worden.',
		'userHasBeenBannedFromRoom': '%s ist aus dem Raum verbannt worden.',
		'userChangedNick': '%1$s hat den Nicknamen zu %2$s geändert.',

		'presenceUnknownWarningSubject': 'Hinweis:',
		'presenceUnknownWarning'       : 'Dieser Benutzer könnte bereits abgemeldet sein. Wir können seine Anwesenheit nicht verfolgen.',

		'dateFormat': 'dd.mm.yyyy',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole'			: 'Moderator',
		'tooltipIgnored'		: 'Du ignorierst diesen Benutzer',
		'tooltipEmoticons'		: 'Smileys',
		'tooltipSound'			: 'Ton abspielen bei neuen privaten Nachrichten',
		'tooltipAutoscroll'		: 'Autoscroll',
		'tooltipStatusmessage'	: 'Statusnachrichten anzeigen',
		'tooltipAdministration'	: 'Raum Administration',
		'tooltipUsercount'		: 'Anzahl Benutzer im Raum',

		'enterRoomPassword' : 'Raum "%s" ist durch ein Passwort geschützt.',
		'enterRoomPasswordSubmit' : 'Raum betreten',
		'passwordEnteredInvalid' : 'Inkorrektes Passwort für Raum "%s".',

		'nicknameConflict': 'Der Benutzername wird bereits verwendet. Bitte wähle einen anderen.',

		'errorMembersOnly': 'Du kannst den Raum "%s" nicht betreten: Ungenügende Rechte.',
		'errorMaxOccupantsReached': 'Du kannst den Raum "%s" nicht betreten: Benutzerlimit erreicht.',
		'errorAutojoinMissing': 'Keine "autojoin" Konfiguration gefunden. Bitte setze eine konfiguration um fortzufahren.',

		'antiSpamMessage' : 'Bitte nicht spammen. Du wurdest für eine kurze Zeit blockiert.'
	},
	'fr' : {
		'status': 'Status : %s',
		'statusConnecting': 'Connexion…',
		'statusConnected' : 'Connecté.',
		'statusDisconnecting': 'Déconnexion…',
		'statusDisconnected' : 'Déconnecté.',
		'statusAuthfail': 'L\'authentification a échoué',

		'roomSubject'  : 'Sujet :',
		'messageSubmit': 'Envoyer',

		'labelUsername': 'Nom d\'utilisateur :',
		'labelPassword': 'Mot de passe :',
		'loginSubmit'  : 'Connexion',
		'loginInvalid'  : 'JID invalide',

		'reason'				: 'Motif :',
		'subject'				: 'Titre :',
		'reasonWas'				: 'Motif : %s.',
		'kickActionLabel'		: 'Kick',
		'youHaveBeenKickedBy'   : 'Vous avez été expulsé du salon %1$s (%2$s)',
		'youHaveBeenKicked'     : 'Vous avez été expulsé du salon %s',
		'banActionLabel'		: 'Ban',
		'youHaveBeenBannedBy'   : 'Vous avez été banni du salon %1$s (%2$s)',
		'youHaveBeenBanned'     : 'Vous avez été banni du salon %s',

		'privateActionLabel' : 'Chat privé',
		'ignoreActionLabel'  : 'Ignorer',
		'unignoreActionLabel' : 'Ne plus ignorer',

		'setSubjectActionLabel': 'Changer le sujet',

		'administratorMessageSubject' : 'Administrateur',

		'userJoinedRoom'           : '%s vient d\'entrer dans le salon.',
		'userLeftRoom'             : '%s vient de quitter le salon.',
		'userHasBeenKickedFromRoom': '%s a été expulsé du salon.',
		'userHasBeenBannedFromRoom': '%s a été banni du salon.',

		'presenceUnknownWarningSubject': 'Note :',
		'presenceUnknownWarning'       : 'Cet utilisateur n\'est malheureusement plus connecté, le message ne sera pas envoyé.',

		'dateFormat': 'dd/mm/yyyy',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole'			: 'Modérateur',
		'tooltipIgnored'		: 'Vous ignorez cette personne',
		'tooltipEmoticons'		: 'Smileys',
		'tooltipSound'			: 'Jouer un son lors de la réception de nouveaux messages privés',
		'tooltipAutoscroll'		: 'Défilement automatique',
		'tooltipStatusmessage'	: 'Messages d\'état',
		'tooltipAdministration'	: 'Administration du salon',
		'tooltipUsercount'		: 'Nombre d\'utilisateurs dans le salon',

		'enterRoomPassword' : 'Le salon "%s" est protégé par un mot de passe.',
		'enterRoomPasswordSubmit' : 'Entrer dans le salon',
		'passwordEnteredInvalid' : 'Le mot de passe pour le salon "%s" est invalide.',

		'nicknameConflict': 'Le nom d\'utilisateur est déjà utilisé. Veuillez en choisir un autre.',

		'errorMembersOnly': 'Vous ne pouvez pas entrer dans le salon "%s" : droits insuffisants.',
		'errorMaxOccupantsReached': 'Vous ne pouvez pas entrer dans le salon "%s": Limite d\'utilisateur atteint.',

		'antiSpamMessage' : 'Merci de ne pas envoyer de spam. Vous avez été bloqué pendant une courte période..'
	},
	'nl' : {
		'status': 'Status: %s',
		'statusConnecting': 'Verbinding maken...',
		'statusConnected' : 'Verbinding is gereed',
		'statusDisconnecting': 'Verbinding verbreken...',
		'statusDisconnected' : 'Verbinding is verbroken',
		'statusAuthfail': 'Authenticatie is mislukt',

		'roomSubject'  : 'Onderwerp:',
		'messageSubmit': 'Verstuur',

		'labelUsername': 'Gebruikersnaam:',
		'labelPassword': 'Wachtwoord:',
		'loginSubmit'  : 'Inloggen',
		'loginInvalid'  : 'JID is onjuist',

		'reason'				: 'Reden:',
		'subject'				: 'Onderwerp:',
		'reasonWas'				: 'De reden was: %s.',
		'kickActionLabel'		: 'Verwijderen',
		'youHaveBeenKickedBy'   : 'Je bent verwijderd van %1$s door %2$s',
		'youHaveBeenKicked'     : 'Je bent verwijderd van %s',
		'banActionLabel'		: 'Blokkeren',
		'youHaveBeenBannedBy'   : 'Je bent geblokkeerd van %1$s door %2$s',
		'youHaveBeenBanned'     : 'Je bent geblokkeerd van %s',

		'privateActionLabel' : 'Prive gesprek',
		'ignoreActionLabel'  : 'Negeren',
		'unignoreActionLabel' : 'Niet negeren',

		'setSubjectActionLabel': 'Onderwerp wijzigen',

		'administratorMessageSubject' : 'Beheerder',

		'userJoinedRoom'           : '%s komt de chat binnen.',
		'userLeftRoom'             : '%s heeft de chat verlaten.',
		'userHasBeenKickedFromRoom': '%s is verwijderd.',
		'userHasBeenBannedFromRoom': '%s is geblokkeerd.',

		'presenceUnknownWarningSubject': 'Mededeling:',
		'presenceUnknownWarning'       : 'Deze gebruiker is waarschijnlijk offline, we kunnen zijn/haar aanwezigheid niet vaststellen.',

		'dateFormat': 'dd.mm.yyyy',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole'			: 'Moderator',
		'tooltipIgnored'		: 'Je negeert deze gebruiker',
		'tooltipEmoticons'		: 'Emotie-iconen',
		'tooltipSound'			: 'Speel een geluid af bij nieuwe privé berichten.',
		'tooltipAutoscroll'		: 'Automatisch scrollen',
		'tooltipStatusmessage'	: 'Statusberichten weergeven',
		'tooltipAdministration'	: 'Instellingen',
		'tooltipUsercount'		: 'Gebruikers',

		'enterRoomPassword' : 'De Chatroom "%s" is met een wachtwoord beveiligd.',
		'enterRoomPasswordSubmit' : 'Ga naar Chatroom',
		'passwordEnteredInvalid' : 'Het wachtwoord voor de Chatroom "%s" is onjuist.',

		'nicknameConflict': 'De gebruikersnaam is reeds in gebruik. Probeer a.u.b. een andere gebruikersnaam.',

		'errorMembersOnly': 'Je kunt niet deelnemen aan de Chatroom "%s": Je hebt onvoldoende rechten.',
		'errorMaxOccupantsReached': 'Je kunt niet deelnemen aan de Chatroom "%s": Het maximum aantal gebruikers is bereikt.',

		'antiSpamMessage' : 'Het is niet toegestaan om veel berichten naar de server te versturen. Je bent voor een korte periode geblokkeerd.'
	},
	'es': {
		'status': 'Estado: %s',
		'statusConnecting': 'Conectando...',
		'statusConnected' : 'Conectado',
		'statusDisconnecting': 'Desconectando...',
		'statusDisconnected' : 'Desconectado',
		'statusAuthfail': 'Falló la autenticación',

		'roomSubject'  : 'Asunto:',
		'messageSubmit': 'Enviar',

		'labelUsername': 'Usuario:',
		'labelPassword': 'Clave:',
		'loginSubmit'  : 'Entrar',
		'loginInvalid'  : 'JID no válido',

		'reason'				: 'Razón:',
		'subject'				: 'Asunto:',
		'reasonWas'				: 'La razón fue: %s.',
		'kickActionLabel'		: 'Expulsar',
		'youHaveBeenKickedBy'   : 'Has sido expulsado de %1$s por %2$s',
		'youHaveBeenKicked'     : 'Has sido expulsado de %s',
		'banActionLabel'		: 'Prohibir',
		'youHaveBeenBannedBy'   : 'Has sido expulsado permanentemente de %1$s por %2$s',
		'youHaveBeenBanned'     : 'Has sido expulsado permanentemente de %s',

		'privateActionLabel' : 'Chat privado',
		'ignoreActionLabel'  : 'Ignorar',
		'unignoreActionLabel' : 'No ignorar',

		'setSubjectActionLabel': 'Cambiar asunto',

		'administratorMessageSubject' : 'Administrador',

		'userJoinedRoom'           : '%s se ha unido a la sala.',
		'userLeftRoom'             : '%s ha dejado la sala.',
		'userHasBeenKickedFromRoom': '%s ha sido expulsado de la sala.',
		'userHasBeenBannedFromRoom': '%s ha sido expulsado permanentemente de la sala.',

		'presenceUnknownWarningSubject': 'Atención:',
		'presenceUnknownWarning'       : 'Éste usuario podría estar desconectado..',

		'dateFormat': 'dd.mm.yyyy',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole'			: 'Moderador',
		'tooltipIgnored'		: 'Ignoras a éste usuario',
		'tooltipEmoticons'		: 'Emoticonos',
		'tooltipSound'			: 'Reproducir un sonido para nuevos mensajes privados',
		'tooltipAutoscroll'		: 'Desplazamiento automático',
		'tooltipStatusmessage'	: 'Mostrar mensajes de estado',
		'tooltipAdministration'	: 'Administración de la sala',
		'tooltipUsercount'		: 'Usuarios en la sala',

		'enterRoomPassword' : 'La sala "%s" está protegida mediante contraseña.',
		'enterRoomPasswordSubmit' : 'Unirse a la sala',
		'passwordEnteredInvalid' : 'Contraseña incorrecta para la sala "%s".',

		'nicknameConflict': 'El nombre de usuario ya está siendo utilizado. Por favor elija otro.',

		'errorMembersOnly': 'No se puede unir a la sala "%s": no tiene privilegios suficientes.',
		'errorMaxOccupantsReached': 'No se puede unir a la sala "%s": demasiados participantes.',

		'antiSpamMessage' : 'Por favor, no hagas spam. Has sido bloqueado temporalmente.'
	},
	'cn': {
		'status': '状态: %s',
		'statusConnecting': '连接中...',
		'statusConnected': '已连接',
		'statusDisconnecting': '断开连接中...',
		'statusDisconnected': '已断开连接',
		'statusAuthfail': '认证失败',

		'roomSubject': '主题:',
		'messageSubmit': '发送',

		'labelUsername': '用户名:',
		'labelPassword': '密码:',
		'loginSubmit': '登录',
		'loginInvalid': '用户名不合法',

		'reason': '原因:',
		'subject': '主题:',
		'reasonWas': '原因是: %s.',
		'kickActionLabel': '踢除',
		'youHaveBeenKickedBy': '你在 %1$s 被管理者 %2$s 请出房间',
		'banActionLabel': '禁言',
		'youHaveBeenBannedBy': '你在 %1$s 被管理者 %2$s 禁言',

		'privateActionLabel': '单独对话',
		'ignoreActionLabel': '忽略',
		'unignoreActionLabel': '不忽略',

		'setSubjectActionLabel': '变更主题',

		'administratorMessageSubject': '管理员',

		'userJoinedRoom': '%s 加入房间',
		'userLeftRoom': '%s 离开房间',
		'userHasBeenKickedFromRoom': '%s 被请出这个房间',
		'userHasBeenBannedFromRoom': '%s 被管理者禁言',

		'presenceUnknownWarningSubject': '注意:',
		'presenceUnknownWarning': '这个会员可能已经下线，不能追踪到他的连接信息',

		'dateFormat': 'dd.mm.yyyy',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole': '管理',
		'tooltipIgnored': '你忽略了这个会员',
		'tooltipEmoticons': '表情',
		'tooltipSound': '新消息发音',
		'tooltipAutoscroll': '滚动条',
		'tooltipStatusmessage': '禁用状态消息',
		'tooltipAdministration': '房间管理',
		'tooltipUsercount': '房间占有者',

		'enterRoomPassword': '登录房间 "%s" 需要密码.',
		'enterRoomPasswordSubmit': '加入房间',
		'passwordEnteredInvalid': '登录房间 "%s" 的密码不正确',

		'nicknameConflict': '用户名已经存在，请另选一个',

		'errorMembersOnly': '您的权限不够，不能登录房间 "%s" ',
		'errorMaxOccupantsReached': '房间 "%s" 的人数已达上限，您不能登录',

		'antiSpamMessage': '因为您在短时间内发送过多的消息 服务器要阻止您一小段时间。'
	},
	'ja' : {
		'status'        : 'ステータス: %s',
		'statusConnecting'  : '接続中…',
		'statusConnected'   : '接続されました',
		'statusDisconnecting'   : 'ディスコネクト中…',
		'statusDisconnected'    : 'ディスコネクトされました',
		'statusAuthfail'    : '認証に失敗しました',

		'roomSubject'       : 'トピック：',
		'messageSubmit'     : '送信',

		'labelUsername'     : 'ユーザーネーム：',
		'labelPassword'     : 'パスワード：',
		'loginSubmit'       : 'ログイン',
		'loginInvalid'      : 'ユーザーネームが正しくありません',

		'reason'        : '理由：',
		'subject'       : 'トピック：',
		'reasonWas'     : '理由: %s。',
		'kickActionLabel'   : 'キック',
		'youHaveBeenKickedBy'   : 'あなたは%2$sにより%1$sからキックされました。',
		'youHaveBeenKicked'     : 'あなたは%sからキックされました。',
		'banActionLabel'    : 'アカウントバン',
		'youHaveBeenBannedBy'   : 'あなたは%2$sにより%1$sからアカウントバンされました。',
		'youHaveBeenBanned'     : 'あなたは%sからアカウントバンされました。',

		'privateActionLabel'    : 'プライベートメッセージ',
		'ignoreActionLabel' : '無視する',
		'unignoreActionLabel'   : '無視をやめる',

		'setSubjectActionLabel'     : 'トピックを変える',

		'administratorMessageSubject'   : '管理者',

		'userJoinedRoom'        : '%sは入室しました。',
		'userLeftRoom'          : '%sは退室しました。',
		'userHasBeenKickedFromRoom' : '%sは部屋からキックされました。',
		'userHasBeenBannedFromRoom' : '%sは部屋からアカウントバンされました。',

		'presenceUnknownWarningSubject' : '忠告：',
		'presenceUnknownWarning'    : 'このユーザーのステータスは不明です。',

		'dateFormat'        : 'dd.mm.yyyy',
		'timeFormat'        : 'HH:MM:ss',

		'tooltipRole'       : 'モデレーター',
		'tooltipIgnored'    : 'このユーザーを無視設定にしている',
		'tooltipEmoticons'  : '絵文字',
		'tooltipSound'      : '新しいメッセージが届くたびに音を鳴らす',
		'tooltipAutoscroll' : 'オートスクロール',
		'tooltipStatusmessage'  : 'ステータスメッセージを表示',
		'tooltipAdministration' : '部屋の管理',
		'tooltipUsercount'  : 'この部屋の参加者の数',

		'enterRoomPassword'     : '"%s"の部屋に入るにはパスワードが必要です。',
		'enterRoomPasswordSubmit'   : '部屋に入る',
		'passwordEnteredInvalid'    : '"%s"のパスワードと異なるパスワードを入力しました。',

		'nicknameConflict'  : 'このユーザーネームはすでに利用されているため、別のユーザーネームを選んでください。',

		'errorMembersOnly'      : '"%s"の部屋に入ることができません: 利用権限を満たしていません。',
		'errorMaxOccupantsReached'  : '"%s"の部屋に入ることができません: 参加者の数はすでに上限に達しました。',

		'antiSpamMessage'   : 'スパムなどの行為はやめてください。あなたは一時的にブロックされました。'
	},
	'sv' : {
		'status': 'Status: %s',
		'statusConnecting': 'Ansluter...',
		'statusConnected' : 'Ansluten',
		'statusDisconnecting': 'Kopplar från...',
		'statusDisconnected' : 'Frånkopplad',
		'statusAuthfail': 'Autentisering misslyckades',

		'roomSubject'  : 'Ämne:',
		'messageSubmit': 'Skicka',

		'labelUsername': 'Användarnamn:',
		'labelPassword': 'Lösenord:',
		'loginSubmit'  : 'Logga in',
		'loginInvalid'  : 'Ogiltigt JID',

		'reason'                : 'Anledning:',
		'subject'               : 'Ämne:',
		'reasonWas'             : 'Anledningen var: %s.',
		'kickActionLabel'       : 'Sparka ut',
		'youHaveBeenKickedBy'   : 'Du har blivit utsparkad från %2$s av %1$s',
		'youHaveBeenKicked'     : 'Du har blivit utsparkad från %s',
		'banActionLabel'        : 'Bannlys',
		'youHaveBeenBannedBy'   : 'Du har blivit bannlyst från %1$s av %2$s',
		'youHaveBeenBanned'     : 'Du har blivit bannlyst från %s',

		'privateActionLabel' : 'Privat chatt',
		'ignoreActionLabel'  : 'Blockera',
		'unignoreActionLabel' : 'Avblockera',

		'setSubjectActionLabel': 'Ändra ämne',

		'administratorMessageSubject' : 'Administratör',

		'userJoinedRoom'           : '%s kom in i rummet.',
		'userLeftRoom'             : '%s har lämnat rummet.',
		'userHasBeenKickedFromRoom': '%s har blivit utsparkad ur rummet.',
		'userHasBeenBannedFromRoom': '%s har blivit bannlyst från rummet.',

		'presenceUnknownWarningSubject': 'Notera:',
		'presenceUnknownWarning'       : 'Denna användare kan vara offline. Vi kan inte följa dennes närvaro.',

		'dateFormat': 'yyyy-mm-dd',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole'           : 'Moderator',
		'tooltipIgnored'        : 'Du blockerar denna användare',
		'tooltipEmoticons'      : 'Smilies',
		'tooltipSound'          : 'Spela upp ett ljud vid nytt privat meddelande',
		'tooltipAutoscroll'     : 'Autoskrolla',
		'tooltipStatusmessage'  : 'Visa statusmeddelanden',
		'tooltipAdministration' : 'Rumadministrering',
		'tooltipUsercount'      : 'Antal användare i rummet',

		'enterRoomPassword' : 'Rummet "%s" är lösenordsskyddat.',
		'enterRoomPasswordSubmit' : 'Anslut till rum',
		'passwordEnteredInvalid' : 'Ogiltigt lösenord för rummet "%s".',

		'nicknameConflict': 'Upptaget användarnamn. Var god välj ett annat.',

		'errorMembersOnly': 'Du kan inte ansluta till rummet "%s": Otillräckliga rättigheter.',
		'errorMaxOccupantsReached': 'Du kan inte ansluta till rummet "%s": Rummet är fullt.',

		'antiSpamMessage' : 'Var god avstå från att spamma. Du har blivit blockerad för en kort stund.'
	},
	'it' : {
		'status': 'Stato: %s',
		'statusConnecting': 'Connessione...',
		'statusConnected' : 'Connessione',
		'statusDisconnecting': 'Disconnessione...',
		'statusDisconnected' : 'Disconnesso',
		'statusAuthfail': 'Autenticazione fallita',

		'roomSubject'  : 'Oggetto:',
		'messageSubmit': 'Invia',

		'labelUsername': 'Nome utente:',
		'labelPassword': 'Password:',
		'loginSubmit'  : 'Login',
		'loginInvalid'  : 'JID non valido',

		'reason'                : 'Ragione:',
		'subject'               : 'Oggetto:',
		'reasonWas'             : 'Ragione precedente: %s.',
		'kickActionLabel'       : 'Espelli',
		'youHaveBeenKickedBy'   : 'Sei stato espulso da %2$s da %1$s',
		'youHaveBeenKicked'     : 'Sei stato espulso da %s',
		'banActionLabel'        : 'Escluso',
		'youHaveBeenBannedBy'   : 'Sei stato escluso da %1$s da %2$s',
		'youHaveBeenBanned'     : 'Sei stato escluso da %s',

		'privateActionLabel' : 'Stanza privata',
		'ignoreActionLabel'  : 'Ignora',
		'unignoreActionLabel' : 'Non ignorare',

		'setSubjectActionLabel': 'Cambia oggetto',

		'administratorMessageSubject' : 'Amministratore',

		'userJoinedRoom'           : '%s si è unito alla stanza.',
		'userLeftRoom'             : '%s ha lasciato la stanza.',
		'userHasBeenKickedFromRoom': '%s è stato espulso dalla stanza.',
		'userHasBeenBannedFromRoom': '%s è stato escluso dalla stanza.',

		'presenceUnknownWarningSubject': 'Nota:',
		'presenceUnknownWarning'       : 'Questo utente potrebbe essere offline. Non possiamo tracciare la sua presenza.',

		'dateFormat': 'dd/mm/yyyy',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole'           : 'Moderatore',
		'tooltipIgnored'        : 'Stai ignorando questo utente',
		'tooltipEmoticons'      : 'Emoticons',
		'tooltipSound'          : 'Riproduci un suono quando arrivano messaggi privati',
		'tooltipAutoscroll'     : 'Autoscroll',
		'tooltipStatusmessage'  : 'Mostra messaggi di stato',
		'tooltipAdministration' : 'Amministrazione stanza',
		'tooltipUsercount'      : 'Partecipanti alla stanza',

		'enterRoomPassword' : 'La stanza "%s" è protetta da password.',
		'enterRoomPasswordSubmit' : 'Unisciti alla stanza',
		'passwordEnteredInvalid' : 'Password non valida per la stanza "%s".',

		'nicknameConflict': 'Nome utente già in uso. Scegline un altro.',

		'errorMembersOnly': 'Non puoi unirti alla stanza "%s": Permessi insufficienti.',
		'errorMaxOccupantsReached': 'Non puoi unirti alla stanza "%s": Troppi partecipanti.',

		'antiSpamMessage' : 'Per favore non scrivere messaggi pubblicitari. Sei stato bloccato per un po\' di tempo.'
	},
	'pt': {
		'status': 'Status: %s',
		'statusConnecting': 'Conectando...',
		'statusConnected' : 'Conectado',
		'statusDisconnecting': 'Desligando...',
		'statusDisconnected' : 'Desligado',
		'statusAuthfail': 'Falha na autenticação',

		'roomSubject'  : 'Assunto:',
		'messageSubmit': 'Enviar',

		'labelUsername': 'Usuário:',
		'labelPassword': 'Senha:',
		'loginSubmit'  : 'Entrar',
		'loginInvalid'  : 'JID inválido',

		'reason'				: 'Motivo:',
		'subject'				: 'Assunto:',
		'reasonWas'				: 'O motivo foi: %s.',
		'kickActionLabel'		: 'Excluir',
		'youHaveBeenKickedBy'   : 'Você foi excluido de %1$s por %2$s',
		'youHaveBeenKicked'     : 'Você foi excluido de %s',
		'banActionLabel'		: 'Bloquear',
		'youHaveBeenBannedBy'   : 'Você foi excluido permanentemente de %1$s por %2$s',
		'youHaveBeenBanned'     : 'Você foi excluido permanentemente de %s',

		'privateActionLabel' : 'Bate-papo privado',
		'ignoreActionLabel'  : 'Ignorar',
		'unignoreActionLabel' : 'Não ignorar',

		'setSubjectActionLabel': 'Trocar Assunto',

		'administratorMessageSubject' : 'Administrador',

		'userJoinedRoom'           : '%s entrou na sala.',
		'userLeftRoom'             : '%s saiu da sala.',
		'userHasBeenKickedFromRoom': '%s foi excluido da sala.',
		'userHasBeenBannedFromRoom': '%s foi excluido permanentemente da sala.',

		'presenceUnknownWarning'       : 'Este usuário pode estar desconectado. Não é possível determinar o status.',

		'dateFormat': 'dd.mm.yyyy',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole'			: 'Moderador',
		'tooltipIgnored'		: 'Você ignora este usuário',
		'tooltipEmoticons'		: 'Emoticons',
		'tooltipSound'			: 'Reproduzir o som para novas mensagens privados',
		'tooltipAutoscroll'		: 'Deslocamento automático',
		'tooltipStatusmessage'	: 'Mostrar mensagens de status',
		'tooltipAdministration'	: 'Administração da sala',
		'tooltipUsercount'		: 'Usuários na sala',

		'enterRoomPassword' : 'A sala "%s" é protegida por senha.',
		'enterRoomPasswordSubmit' : 'Junte-se à sala',
		'passwordEnteredInvalid' : 'Senha incorreta para a sala "%s".',

		'nicknameConflict': 'O nome de usuário já está em uso. Por favor, escolha outro.',

		'errorMembersOnly': 'Você não pode participar da sala "%s":  privilégios insuficientes.',
		'errorMaxOccupantsReached': 'Você não pode participar da sala "%s": muitos participantes.',

		'antiSpamMessage' : 'Por favor, não envie spam. Você foi bloqueado temporariamente.'
	},
	'pt_br' : {
		'status': 'Estado: %s',
		'statusConnecting': 'Conectando...',
		'statusConnected' : 'Conectado',
		'statusDisconnecting': 'Desconectando...',
		'statusDisconnected' : 'Desconectado',
		'statusAuthfail': 'Autenticação falhou',

		'roomSubject' : 'Assunto:',
		'messageSubmit': 'Enviar',

		'labelUsername': 'Usuário:',
		'labelPassword': 'Senha:',
		'loginSubmit' : 'Entrar',
		'loginInvalid' : 'JID inválido',

		'reason'                                : 'Motivo:',
		'subject'                                : 'Assunto:',
		'reasonWas'                                : 'Motivo foi: %s.',
		'kickActionLabel'                : 'Derrubar',
		'youHaveBeenKickedBy' : 'Você foi derrubado de %2$s por %1$s',
		'youHaveBeenKicked' : 'Você foi derrubado de %s',
		'banActionLabel'                : 'Banir',
		'youHaveBeenBannedBy' : 'Você foi banido de %1$s por %2$s',
		'youHaveBeenBanned' : 'Você foi banido de %s',

		'privateActionLabel' : 'Conversa privada',
		'ignoreActionLabel' : 'Ignorar',
		'unignoreActionLabel' : 'Não ignorar',

		'setSubjectActionLabel': 'Mudar Assunto',

		'administratorMessageSubject' : 'Administrador',

		'userJoinedRoom' : '%s entrou na sala.',
		'userLeftRoom' : '%s saiu da sala.',
		'userHasBeenKickedFromRoom': '%s foi derrubado da sala.',
		'userHasBeenBannedFromRoom': '%s foi banido da sala.',

		'presenceUnknownWarningSubject': 'Aviso:',
		'presenceUnknownWarning' : 'Este usuário pode estar desconectado.. Não conseguimos rastrear sua presença..',

		'dateFormat': 'dd.mm.yyyy',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole'                        : 'Moderador',
		'tooltipIgnored'                : 'Você ignora este usuário',
		'tooltipEmoticons'                : 'Emoticons',
		'tooltipSound'                        : 'Tocar som para novas mensagens privadas',
		'tooltipAutoscroll'                : 'Auto-rolagem',
		'tooltipStatusmessage'        : 'Exibir mensagens de estados',
		'tooltipAdministration'        : 'Administração de Sala',
		'tooltipUsercount'                : 'Participantes da Sala',

		'enterRoomPassword' : 'Sala "%s" é protegida por senha.',
		'enterRoomPasswordSubmit' : 'Entrar na sala',
		'passwordEnteredInvalid' : 'Senha inváida para sala "%s".',

		'nicknameConflict': 'Nome de usuário já em uso. Por favor escolha outro.',

		'errorMembersOnly': 'Você não pode entrar na sala "%s": privilégios insuficientes.',
		'errorMaxOccupantsReached': 'Você não pode entrar na sala "%s": máximo de participantes atingido.',

		'antiSpamMessage' : 'Por favor, não faça spam. Você foi bloqueado temporariamente.'
	},
	'ru' : {
		'status': 'Статус: %s',
		'statusConnecting': 'Подключение...',
		'statusConnected' : 'Подключено',
		'statusDisconnecting': 'Отключение...',
		'statusDisconnected' : 'Отключено',
		'statusAuthfail': 'Неверный логин',

		'roomSubject'  : 'Топик:',
		'messageSubmit': 'Послать',

		'labelUsername': 'Имя:',
		'labelPassword': 'Пароль:',
		'loginSubmit'  : 'Логин',
		'loginInvalid'  : 'Неверный JID',

		'reason'				: 'Причина:',
		'subject'				: 'Топик:',
		'reasonWas'				: 'Причина была: %s.',
		'kickActionLabel'		: 'Выбросить',
		'youHaveBeenKickedBy'   : 'Пользователь %1$s выбросил вас из чата %2$s',
		'youHaveBeenKicked'     : 'Вас выбросили из чата %s',
		'banActionLabel'		: 'Запретить доступ',
		'youHaveBeenBannedBy'   : 'Пользователь %1$s запретил вам доступ в чат %2$s',
		'youHaveBeenBanned'     : 'Вам запретили доступ в чат %s',

		'privateActionLabel' : 'Один-на-один чат',
		'ignoreActionLabel'  : 'Игнорировать',
		'unignoreActionLabel' : 'Отменить игнорирование',

		'setSubjectActionLabel': 'Изменить топик',

		'administratorMessageSubject' : 'Администратор',

		'userJoinedRoom'           : '%s вошёл в чат.',
		'userLeftRoom'             : '%s вышел из чата.',
		'userHasBeenKickedFromRoom': '%s выброшен из чата.',
		'userHasBeenBannedFromRoom': '%s запрещён доступ в чат.',

		'presenceUnknownWarningSubject': 'Уведомление:',
		'presenceUnknownWarning'       : 'Этот пользователь вероятнее всего оффлайн.',

		'dateFormat': 'mm.dd.yyyy',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole'			: 'Модератор',
		'tooltipIgnored'		: 'Вы игнорируете этого пользователя.',
		'tooltipEmoticons'		: 'Смайлики',
		'tooltipSound'			: 'Озвучивать новое частное сообщение',
		'tooltipAutoscroll'		: 'Авто-прокручивание',
		'tooltipStatusmessage'	: 'Показывать статус сообщения',
		'tooltipAdministration'	: 'Администрирование чат комнаты',
		'tooltipUsercount'		: 'Участники чата',

		'enterRoomPassword' : 'Чат комната "%s" защищена паролем.',
		'enterRoomPasswordSubmit' : 'Войти в чат',
		'passwordEnteredInvalid' : 'Неверный пароль для комнаты "%s".',

		'nicknameConflict': 'Это имя уже используется. Пожалуйста выберите другое имя.',

		'errorMembersOnly': 'Вы не можете войти в чат "%s": Недостаточно прав доступа.',
		'errorMaxOccupantsReached': 'Вы не можете войти в чат "%s": Слишком много участников.',

		'antiSpamMessage' : 'Пожалуйста не рассылайте спам. Вас заблокировали на короткое время.'
	},
	'ca': {
		'status': 'Estat: %s',
		'statusConnecting': 'Connectant...',
		'statusConnected' : 'Connectat',
		'statusDisconnecting': 'Desconnectant...',
		'statusDisconnected' : 'Desconnectat',
		'statusAuthfail': 'Ha fallat la autenticació',

		'roomSubject'  : 'Assumpte:',
		'messageSubmit': 'Enviar',

		'labelUsername': 'Usuari:',
		'labelPassword': 'Clau:',
		'loginSubmit'  : 'Entrar',
		'loginInvalid'  : 'JID no vàlid',

		'reason'                : 'Raó:',
		'subject'               : 'Assumpte:',
		'reasonWas'             : 'La raó ha estat: %s.',
		'kickActionLabel'       : 'Expulsar',
		'youHaveBeenKickedBy'   : 'Has estat expulsat de %1$s per %2$s',
		'youHaveBeenKicked'     : 'Has estat expulsat de %s',
		'banActionLabel'        : 'Prohibir',
		'youHaveBeenBannedBy'   : 'Has estat expulsat permanentment de %1$s per %2$s',
		'youHaveBeenBanned'     : 'Has estat expulsat permanentment de %s',

		'privateActionLabel' : 'Xat privat',
		'ignoreActionLabel'  : 'Ignorar',
		'unignoreActionLabel' : 'No ignorar',

		'setSubjectActionLabel': 'Canviar assumpte',

		'administratorMessageSubject' : 'Administrador',

		'userJoinedRoom'           : '%s ha entrat a la sala.',
		'userLeftRoom'             : '%s ha deixat la sala.',
		'userHasBeenKickedFromRoom': '%s ha estat expulsat de la sala.',
		'userHasBeenBannedFromRoom': '%s ha estat expulsat permanentment de la sala.',

		'presenceUnknownWarningSubject': 'Atenció:',
		'presenceUnknownWarning'       : 'Aquest usuari podria estar desconnectat ...',

		'dateFormat': 'dd.mm.yyyy',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole'           : 'Moderador',
		'tooltipIgnored'        : 'Estàs ignorant aquest usuari',
		'tooltipEmoticons'      : 'Emoticones',
		'tooltipSound'          : 'Reproduir un so per a nous missatges',
		'tooltipAutoscroll'     : 'Desplaçament automàtic',
		'tooltipStatusmessage'  : 'Mostrar missatges d\'estat',
		'tooltipAdministration' : 'Administració de la sala',
		'tooltipUsercount'      : 'Usuaris dins la sala',

		'enterRoomPassword' : 'La sala "%s" està protegida amb contrasenya.',
		'enterRoomPasswordSubmit' : 'Entrar a la sala',
		'passwordEnteredInvalid' : 'Contrasenya incorrecta per a la sala "%s".',

		'nicknameConflict': 'El nom d\'usuari ja s\'està utilitzant. Si us plau, escolleix-ne un altre.',

		'errorMembersOnly': 'No pots unir-te a la sala "%s": no tens prous privilegis.',
		'errorMaxOccupantsReached': 'No pots unir-te a la sala "%s": hi ha masses participants.',

		'antiSpamMessage' : 'Si us plau, no facis spam. Has estat bloquejat temporalment.'
	}
};
