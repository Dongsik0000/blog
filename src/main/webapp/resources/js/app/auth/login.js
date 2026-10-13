// 관리자 로그인 (admin/loginMain.jsp)
App.login = (function(){
	var m$ = {
			form: document.getElementById('loginForm'),  /* 로그인 폼 */
			password: document.getElementById('loginPassword'),  /* 비밀번호 */
			status: document.getElementById('loginStatus'),  /* 안내·오류 문구 */
			submit: document.getElementById('loginSubmit')  /* 로그인 버튼 */
		},

		settings = {
			submitting: false
		},

		url = {
			login: contextPath + '/auth/login',
			next: contextPath + '/admin/posts'
		},

		/* JS 가 켜져 있을 때만 폼을 보인다 */
		init = function(){
			if (!m$.form) return;
			m$.form.hidden = false;
			m$.form.addEventListener('submit', function(e){
				e.preventDefault();
				login();
			});
			m$.password.focus();
		},

		login = function(){
			if (settings.submitting) return;
			if (App.isEmpty(m$.password.value)) {
				setStatus('비밀번호를 입력해 주세요.');
				return;
			}
			settings.submitting = true;
			m$.submit.disabled = true;
			App.post(url.login, {password: m$.password.value})
				.then(function(res){
					if (res.code === App.CODE.SUCCESS) {
						window.location.href = url.next;
						return;
					}
					m$.password.value = '';
					m$.password.focus();
					setStatus(res.message || '로그인하지 못했습니다.');
				})
				.catch(function(){
					setStatus('로그인하지 못했습니다. 잠시 후 다시 시도해 주세요.');
				})
				.finally(function(){
					settings.submitting = false;
					m$.submit.disabled = false;
				});
		},

		setStatus = function(text){
			m$.status.textContent = text;
			m$.status.classList.toggle('is-error', !!text);
		};

	return {
		init: init
	};
}());

document.addEventListener('DOMContentLoaded', function(){
	App.login.init();
});
