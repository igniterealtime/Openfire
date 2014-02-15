/** File: translation.js
 * Candy - Chats are not dead yet.
 *
 * Authors:
 *   - Patrick Stadler <patrick.stadler@gmail.com>
 *   - Michael Weibel <michael.weibel@gmail.com>
 *
 * Copyright:
 *   (c) 2011 Amiado Group AG. All rights reserved.
 */

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

		'antiSpamMessage' : 'Bitte nicht spammen. Du wurdest für eine kurze Zeit blockiert.'
	},
	
	'fr' : {
		'status': 'Status: %s',
		'statusConnecting': 'Connecter...',
		'statusConnected' : 'Connecté.',
		'statusDisconnecting': 'Déconnecter....',
		'statusDisconnected' : 'Déconnecté.',
		'statusAuthfail': 'Authentification a échoué',

		'roomSubject'  : 'Sujet:',
		'messageSubmit': 'Envoyer',

		'labelUsername': 'Nom d\'utilisateur:',
		'labelPassword': 'Mot de passe:',
		'loginSubmit'  : 'Inscription',
		'loginInvalid'  : 'JID invalide',

		'reason'				: 'Justification:',
		'subject'				: 'Titre:',
		'reasonWas'				: 'Justification: %s.',
		'kickActionLabel'		: 'Kick',
		'youHaveBeenKickedBy'   : 'Tu as été expulsé de le salon %1$s (%2$s)',
		'youHaveBeenKicked'     : 'Tu as été expulsé de le salon %s',
		'banActionLabel'		: 'Ban',
		'youHaveBeenBannedBy'   : 'Tu as été banni de le salon %1$s (%2$s)',
		'youHaveBeenBanned'     : 'Tu as été banni de le salon %s',

		'privateActionLabel' : 'Chat privé',
		'ignoreActionLabel'  : 'Ignorer',
		'unignoreActionLabel' : 'Ne plus ignorer',

		'setSubjectActionLabel': 'Changer le sujet',

		'administratorMessageSubject' : 'Administrateur',

		'userJoinedRoom'           : '%s vient d\'entrer dans le salon.',
		'userLeftRoom'             : '%s vient de quitter le salon.',
		'userHasBeenKickedFromRoom': '%s a été expulsé du salon.',
		'userHasBeenBannedFromRoom': '%s a été banni du salon.',

		'presenceUnknownWarningSubject': 'Note:',
		'presenceUnknownWarning'       : 'Cet utilisateur n\'est malheureusement plus connecté, le message ne sera pas envoyé.',

		'dateFormat': 'dd.mm.yyyy',
		'timeFormat': 'HH:MM:ss',

		'tooltipRole'			: 'Modérateur',
		'tooltipIgnored'		: 'Tu ignores cette personne',
		'tooltipEmoticons'		: 'Smileys',
		'tooltipSound'			: 'Jouer un son lorsque tu reçois de nouveaux messages privés',
		'tooltipAutoscroll'		: 'Auto-defilement',
		'tooltipStatusmessage'	: 'Messages d\'état',
		'tooltipAdministration'	: 'Administrer le salon',
		'tooltipUsercount'		: 'Nombre d\'utilisateurs dans le salon',

		'enterRoomPassword' : 'Le salon "%s" est protégé par un mot de passe.',
		'enterRoomPasswordSubmit' : 'Entrer dans le salon',
		'passwordEnteredInvalid' : 'Le mot de passe four le salon "%s" est invalide.',

		'nicknameConflict': 'Le nom d\'utilisateur est déjà utilisé. Choisi un autre.',

		'errorMembersOnly': 'Tu ne peut pas entrer de le salon "%s": droits insuffisants.',
		'errorMaxOccupantsReached': 'Tu ne peut pas entrer de le salon "%s": Limite d\'utilisateur atteint.',

		'antiSpamMessage' : 'S\'il te plaît, pas de spam. Tu as été bloqué pendant une courte période..'
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
	}
};