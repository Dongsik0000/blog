// 글 관리 목록 (admin/postList.jsp): 임시저장 포함 전체 글, 상태별 보기
App.adminPostList = (function(){
	var m$ = {
			list: document.getElementById('adminPostList'),  /* 글 목록 */
			count: document.getElementById('adminPostCount'),  /* 글 수 요약 */
			status: document.getElementById('adminPostStatus'),  /* 오류 문구 */
			filters: document.querySelectorAll('.admin-filter button'),  /* 상태 보기 버튼 */
			logout: document.getElementById('logoutButton')  /* 로그아웃 */
		},

		settings = {
			posts: [],
			status: ''  /* '' 전체, PUBLISHED, DRAFT */
		},

		url = {
			list: contextPath + '/admin/post/list',
			edit: contextPath + '/admin/posts/',
			view: contextPath + '/posts/'
		},

		init = function(){
			bindEvent();
			load();
		},

		bindEvent = function(){
			m$.logout.addEventListener('click', App.logout);
			m$.filters.forEach(function(button){
				button.addEventListener('click', function(){
					settings.status = button.dataset.status;
					m$.filters.forEach(function(b){
						b.classList.toggle('is-active', b === button);
						b.setAttribute('aria-pressed', b === button);
					});
					render();
				});
			});
		},

		load = function(){
			App.post(url.list, {})
				.then(function(res){
					if (res.code !== App.CODE.SUCCESS) {
						setStatus(res.message || '글 목록을 불러오지 못했습니다.');
						return;
					}
					settings.posts = res.data;
					var drafts = res.data.filter(function(p){ return p.status === 'DRAFT'; }).length;
					m$.count.textContent = '글 ' + res.data.length + '개' + (drafts ? ', 그중 임시저장 ' + drafts + '개' : '');
					render();
				})
				.catch(function(){
					setStatus('글 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
				});
		},

		/* 글자는 textContent 로만 넣는다 */
		render = function(){
			var posts = settings.posts.filter(function(p){ return !settings.status || p.status === settings.status; });
			m$.list.replaceChildren();

			if (posts.length === 0) {
				m$.list.appendChild(el('li', 'admin-empty', settings.posts.length ? '이 상태의 글이 없습니다.' : '아직 글이 없습니다. 오른쪽 위 "새 글 쓰기"로 시작해 보세요.'));
				return;
			}

			posts.forEach(function(post){
				var item = el('li', 'admin-post-row'),
					main = el('div', 'admin-post-main'),
					meta = el('p', 'admin-post-meta'),
					title = el('a', 'admin-post-title', post.title),
					actions = el('div', 'admin-post-actions'),
					view = el('a', 'text-link', '보기'),
					edit = el('a', 'button', '수정');

				var label = el('p', 'admin-post-label', post.categoryLabel);
				if (post.status === 'DRAFT') label.appendChild(el('span', 'status-badge', '임시저장'));

				title.href = url.edit + post.id;
				meta.textContent = '발행일 ' + post.publishedAt + ' · 마지막 수정 ' + post.updatedAt;
				view.href = url.view + encodeURIComponent(post.slug);
				view.target = '_blank';
				view.rel = 'noopener';
				edit.href = url.edit + post.id;

				main.appendChild(label);
				main.appendChild(title);
				main.appendChild(meta);
				actions.appendChild(view);
				actions.appendChild(edit);
				item.appendChild(main);
				item.appendChild(actions);
				m$.list.appendChild(item);
			});
		},

		setStatus = function(text){
			m$.status.textContent = text;
			m$.status.classList.toggle('is-error', !!text);
		},

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
	App.adminPostList.init();
});
