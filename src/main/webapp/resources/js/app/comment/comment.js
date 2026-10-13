// 글 본문 아래 댓글: 목록 · 작성 · 삭제 (commentSection.tag)
App.comment = (function(){
	var m$ = {
			section: document.getElementById('comments'),  /* 댓글 영역. data-slug 에 글 slug */
			list: document.getElementById('commentList'),  /* 댓글 목록 */
			count: document.getElementById('commentCount'),  /* 댓글 수 */
			status: document.getElementById('commentStatus'),  /* 안내·오류 문구 */
			form: document.getElementById('commentForm'),  /* 작성 폼 */
			authorName: document.getElementById('commentAuthor'),  /* 이름 */
			password: document.getElementById('commentPassword'),  /* 비밀번호 */
			content: document.getElementById('commentContent'),  /* 내용 */
			website: document.getElementById('commentWebsite'),  /* 봇 방지 숨은 칸 */
			submit: document.getElementById('commentSubmit')  /* 등록 버튼 */
		},

		settings = {
			submitting: false,  /* 등록 버튼 연타로 같은 댓글이 두 번 가지 않게 */
			slug: '',
			admin: false  /* 관리자면 비밀번호 없이 지운다(서버가 세션으로 다시 확인) */
		},

		url = {
			list: contextPath + '/comment/list',
			save: contextPath + '/comment/save',
			remove: contextPath + '/comment/delete'
		},

		/* 댓글 영역이 있는 화면에서만 동작. JS 가 켜져 있을 때만 폼을 보인다 */
		init = function(){
			if (!m$.section) return;
			settings.slug = m$.section.dataset.slug;
			settings.admin = m$.section.dataset.admin === 'true';
			m$.form.hidden = false;
			bindEvent();
			load();
		},

		bindEvent = function(){
			m$.form.addEventListener('submit', function(e){
				e.preventDefault();
				save();
			});

			/* 삭제 폼은 목록을 다시 그릴 때마다 새로 생기므로 목록에 한 번만 걸어 둔다(이벤트 위임) */
			m$.list.addEventListener('submit', function(e){
				var form = e.target.closest('.comment-delete');
				if (!form) return;
				e.preventDefault();
				remove(form.dataset.id, form.elements.password.value);
			});
			m$.list.addEventListener('click', function(e){
				var button = e.target.closest('.comment-admin-delete');
				if (!button || !window.confirm('이 댓글을 삭제할까요?')) return;
				remove(button.dataset.id, '');
			});
		},

		/* 목록을 불러온다. message 가 있으면 다 그린 뒤 안내 문구로 보인다 */
		load = function(message){
			App.post(url.list, {slug: settings.slug})
				.then(function(res){
					if (res.code !== App.CODE.SUCCESS) {
						setStatus(res.message || '댓글을 불러오지 못했습니다.', true);
						return;
					}
					render(res.data);
					setStatus(message || '');
				})
				.catch(function(){
					setStatus('댓글을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.', true);
				});
		},

		/* 사용자가 쓴 글자는 textContent 로만 넣는다 (innerHTML 이면 <script> 같은 글자가 실행된다) */
		render = function(list){
			m$.count.textContent = list.length;
			m$.list.replaceChildren();

			if (list.length === 0) {
				m$.list.appendChild(el('li', 'comment-empty', '첫 댓글을 남겨 주세요.'));
				return;
			}

			list.forEach(function(comment){
				var item = el('li', 'comment-item' + (comment.isPrompt ? ' is-author' : '')),
					head = el('div', 'comment-head'),
					time = el('time', '', comment.createdAt);

				head.appendChild(el('strong', '', comment.authorName));
				if (comment.isPrompt) head.appendChild(el('span', 'comment-badge', '작성자'));
				time.dateTime = comment.createdAt.replace(' ', 'T');
				head.appendChild(time);

				item.appendChild(head);
				item.appendChild(el('p', 'comment-body', comment.content));
				item.appendChild(settings.admin ? adminDeleteButton(comment.id) : deleteForm(comment.id));
				m$.list.appendChild(item);
			});
		},

		/* 관리자: 비밀번호 없이 바로 삭제 */
		adminDeleteButton = function(id){
			var button = el('button', 'comment-admin-delete', '삭제');
			button.type = 'button';
			button.dataset.id = id;
			return button;
		},

		/* 댓글마다 접어 둔 삭제 폼: 작성할 때 쓴 비밀번호 입력 */
		deleteForm = function(id){
			var details = el('details', 'comment-delete-toggle'),
				form = el('form', 'comment-delete'),
				label = el('label', 'sr-only', '삭제 비밀번호'),
				input = el('input'),
				button = el('button', 'button', '삭제');

			input.type = 'password';
			input.name = 'password';
			input.id = 'commentDeletePassword' + id;
			input.required = true;
			input.autocomplete = 'current-password';
			input.placeholder = '비밀번호';
			label.htmlFor = input.id;
			button.type = 'submit';
			form.dataset.id = id;

			form.appendChild(label);
			form.appendChild(input);
			form.appendChild(button);
			details.appendChild(el('summary', '', '삭제'));
			details.appendChild(form);
			return details;
		},

		save = function(){
			if (settings.submitting) return;
			if (App.isEmpty(m$.authorName.value) || App.isEmpty(m$.content.value)) {
				setStatus('이름과 댓글을 입력해 주세요.', true);
				return;
			}

			settings.submitting = true;
			m$.submit.disabled = true;
			App.post(url.save, {
				slug: settings.slug,
				authorName: m$.authorName.value,
				password: m$.password.value,
				content: m$.content.value,
				website: m$.website.value
			})
				.then(function(res){
					if (res.code !== App.CODE.SUCCESS) {
						setStatus(res.message || '댓글을 등록하지 못했습니다.', true);
						return;
					}
					m$.content.value = '';
					m$.password.value = '';
					load('댓글을 등록했습니다.');
				})
				.catch(function(){
					setStatus('댓글을 등록하지 못했습니다. 잠시 후 다시 시도해 주세요.', true);
				})
				.finally(function(){
					settings.submitting = false;
					m$.submit.disabled = false;
				});
		},

		remove = function(commentId, password){
			App.post(url.remove, {commentId: commentId, password: password})
				.then(function(res){
					if (res.code !== App.CODE.SUCCESS) {
						setStatus(res.message || '댓글을 삭제하지 못했습니다.', true);
						return;
					}
					load('댓글을 삭제했습니다.');
				})
				.catch(function(){
					setStatus('댓글을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.', true);
				});
		},

		setStatus = function(text, isError){
			m$.status.textContent = text;
			m$.status.classList.toggle('is-error', !!isError);
		},

		/* 요소 만들기 도우미: text 는 textContent 로 넣는다 */
		el = function(tag, className, text){
			var node = document.createElement(tag);
			if (className) node.className = className;
			if (text !== undefined && text !== null) node.textContent = text;
			return node;
		};

	return {
		init: init
	};
}());

document.addEventListener('DOMContentLoaded', function(){
	App.comment.init();
});
