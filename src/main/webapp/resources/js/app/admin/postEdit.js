// 글 쓰기·수정 (admin/postEdit.jsp)
App.postEdit = (function(){
	var m$ = {
			form: document.getElementById('postForm'),  /* 편집 폼. data-post-id 가 있으면 수정 */
			title: document.getElementById('editorTitle'),  /* 화면 제목 */
			state: document.getElementById('editorState'),  /* 상태·마지막 저장 */
			status: document.getElementById('editorStatus'),  /* 안내·오류 문구 */
			view: document.getElementById('viewLink'),  /* 글 보기 */
			saveButtons: document.querySelectorAll('#saveDraft, #savePublish'),  /* 임시저장·발행 */
			saveDraft: document.getElementById('saveDraft'),
			savePublish: document.getElementById('savePublish'),
			publishedAt: document.getElementById('postPublishedAt'),
			coverImage: document.getElementById('postCoverImage'),  /* 대표 이미지 주소(숨김) */
			coverFile: document.getElementById('coverFile'),
			coverPreview: document.getElementById('coverPreview'),
			coverPreviewImage: document.getElementById('coverPreviewImage'),
			coverRemove: document.getElementById('coverRemove'),
			danger: document.getElementById('dangerPanel'),
			remove: document.getElementById('deletePost')
		},

		/* 서버 Map 키 = 폼 name. 불러오기·저장에 같이 쓴다 */
		FIELDS = ['title', 'summary', 'body', 'category', 'publishedAt', 'slug', 'coverImage', 'coverAlt',
			'coverCaption', 'coverPrompt', 'hashtags', 'sources'],
		MAX_IMAGE_BYTES = 5 * 1024 * 1024,  /* 서버(web.xml) 업로드 한도 */
		MAX_COVER_WIDTH = 1600,  /* 표지는 이 너비면 충분하다. 더 크거나 한도를 넘으면 브라우저에서 줄여 올린다 */

		settings = {
			id: '',  /* 저장된 글 id. 새 글이면 '' */
			slug: '',
			status: 'DRAFT',  /* 저장된 상태 */
			saving: false,
			dirty: false  /* 저장하지 않은 변경이 있는지 */
		},

		url = {
			detail: contextPath + '/admin/post/detail',
			save: contextPath + '/admin/post/save',
			remove: contextPath + '/admin/post/delete',
			upload: contextPath + '/admin/image/upload',
			edit: contextPath + '/admin/posts/',
			list: contextPath + '/admin/posts',
			view: contextPath + '/posts/'
		},

		init = function(){
			settings.id = m$.form.dataset.postId || '';
			bindEvent();
			if (settings.id) {
				load();
			} else {
				m$.publishedAt.value = today();
				render();
			}
		},

		bindEvent = function(){
			m$.form.addEventListener('input', function(){ settings.dirty = true; });
			m$.form.addEventListener('submit', function(e){ e.preventDefault(); });
			m$.saveButtons.forEach(function(button){
				button.addEventListener('click', function(){ save(button.dataset.status); });
			});
			/* Ctrl+S / Cmd+S: 지금 상태 그대로 저장 */
			document.addEventListener('keydown', function(e){
				if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
					e.preventDefault();
					save(settings.status);
				}
			});
			/* 저장하지 않고 나가려 하면 브라우저가 확인한다 */
			window.addEventListener('beforeunload', function(e){
				if (settings.dirty) e.preventDefault();
			});
			m$.coverFile.addEventListener('change', upload);
			m$.coverRemove.addEventListener('click', function(){
				setCover('');
				settings.dirty = true;
			});
			m$.remove.addEventListener('click', remove);
		},

		load = function(){
			setStatus('글을 불러오는 중입니다.');
			App.post(url.detail, {id: settings.id})
				.then(function(res){
					if (res.code !== App.CODE.SUCCESS) {
						setStatus(res.message || '글을 불러오지 못했습니다.', true);
						return;
					}
					FIELDS.forEach(function(name){
						m$.form.elements[name].value = res.data[name] || '';
					});
					settings.slug = res.data.slug;
					settings.status = res.data.status;
					setCover(res.data.coverImage || '');
					settings.dirty = false;
					setStatus('');
					render();
				})
				.catch(function(){
					setStatus('글을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.', true);
				});
		},

		/* 저장된 상태에 맞춰 버튼 문구·글 보기·삭제를 바꾼다 */
		render = function(){
			var published = settings.status === 'PUBLISHED';
			m$.saveDraft.textContent = published ? '발행 취소' : '임시저장';
			m$.savePublish.textContent = published ? '변경 내용 발행' : '발행';
			m$.state.textContent = settings.id
				? (published ? '발행된 글입니다.' : '임시저장 글입니다. 관리자에게만 보입니다.')
				: '아직 저장하지 않았습니다.';
			m$.view.hidden = !settings.id;
			m$.view.href = url.view + encodeURIComponent(settings.slug);
			m$.danger.hidden = !settings.id;
		},

		/* status: DRAFT(임시저장·발행 취소) 또는 PUBLISHED(발행) */
		save = function(status){
			if (settings.saving) return;
			var data = {id: settings.id, status: status};
			FIELDS.forEach(function(name){
				data[name] = m$.form.elements[name].value;
			});

			settings.saving = true;
			m$.saveButtons.forEach(function(b){ b.disabled = true; });
			setStatus('저장하는 중입니다.');
			App.post(url.save, data)
				.then(function(res){
					if (res.code !== App.CODE.SUCCESS) {
						setStatus(res.message || '저장하지 못했습니다.', true);
						return;
					}
					var isNew = !settings.id;
					settings.id = String(res.data.id);
					settings.slug = res.data.slug;
					settings.status = res.data.status;
					settings.dirty = false;
					m$.form.elements.slug.value = res.data.slug;
					if (isNew) {
						/* 새로 고침해도 같은 글을 고치도록 주소를 바꾼다 */
						history.replaceState(null, '', url.edit + settings.id);
						m$.title.firstChild.textContent = '글 수정';
						document.title = '글 수정 · DevGear';
					}
					render();
					setStatus((status === 'PUBLISHED' ? '발행했습니다. ' : '임시저장했습니다. ') + time());
				})
				.catch(function(){
					setStatus('저장하지 못했습니다. 잠시 후 다시 시도해 주세요. 쓴 내용은 이 화면에 그대로 있습니다.', true);
				})
				.finally(function(){
					settings.saving = false;
					m$.saveButtons.forEach(function(b){ b.disabled = false; });
				});
		},

		upload = function(){
			var file = m$.coverFile.files[0];
			m$.coverFile.value = '';  /* 같은 파일을 다시 골라도 change 가 일어나게 */
			if (!file) return;
			setStatus('이미지를 준비하는 중입니다.');
			shrink(file)
				.then(function(ready){
					if (ready.size > MAX_IMAGE_BYTES) {
						setStatus('이미지는 5MB 이하만 올릴 수 있습니다.', true);
						return null;
					}
					setStatus('이미지를 올리는 중입니다.');
					return App.upload(url.upload, ready);
				})
				.then(function(res){
					if (!res) return;
					if (res.code !== App.CODE.SUCCESS) {
						setStatus(res.message || '이미지를 올리지 못했습니다.', true);
						return;
					}
					setCover(res.data.url);
					settings.dirty = true;
					setStatus('이미지를 올렸습니다. 이미지 설명을 쓰고 저장해 주세요.');
					m$.form.elements.coverAlt.focus();
				})
				.catch(function(err){
					setStatus(err.status === 413 ? '이미지는 5MB 이하만 올릴 수 있습니다.' : '이미지를 올리지 못했습니다. 다른 이미지로 다시 시도해 주세요.', true);
				});
		},

		/* 가로가 MAX_COVER_WIDTH 를 넘거나 용량이 한도를 넘으면 캔버스로 줄여 JPEG 로 바꾼다.
		   GIF 는 캔버스에 그리면 움직임이 사라지므로 그대로 둔다 */
		shrink = function(file){
			if (file.type === 'image/gif') return Promise.resolve(file);
			return new Promise(function(resolve, reject){
				var img = new Image(),
					src = URL.createObjectURL(file);
				img.onload = function(){
					URL.revokeObjectURL(src);
					if (img.naturalWidth <= MAX_COVER_WIDTH && file.size <= MAX_IMAGE_BYTES) {
						resolve(file);
						return;
					}
					var scale = Math.min(1, MAX_COVER_WIDTH / img.naturalWidth),
						canvas = document.createElement('canvas'),
						ctx = canvas.getContext('2d');
					canvas.width = Math.round(img.naturalWidth * scale);
					canvas.height = Math.round(img.naturalHeight * scale);
					ctx.fillStyle = '#ffffff';  /* 투명한 부분이 JPEG 에서 검게 되지 않게 */
					ctx.fillRect(0, 0, canvas.width, canvas.height);
					ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
					canvas.toBlob(function(blob){
						if (blob) resolve(new File([blob], 'cover.jpg', {type: 'image/jpeg'}));
						else reject(new Error('이미지를 변환하지 못했습니다.'));
					}, 'image/jpeg', 0.9);  /* 4컷 만화의 글씨·선이 뭉개지지 않는 품질 */
				};
				img.onerror = function(){
					URL.revokeObjectURL(src);
					reject(new Error('이미지 파일을 읽지 못했습니다.'));
				};
				img.src = src;
			});
		},

		/* 대표 이미지 주소를 숨은 칸에 넣고 미리보기를 바꾼다 */
		setCover = function(path){
			m$.coverImage.value = path;
			m$.coverPreview.hidden = !path;
			m$.coverRemove.hidden = !path;
			m$.coverPreviewImage.src = path ? contextPath + path : '';
		},

		remove = function(){
			if (!window.confirm('이 글을 삭제할까요? 같은 주소는 다시 쓸 수 없습니다.')) return;
			App.post(url.remove, {id: settings.id})
				.then(function(res){
					if (res.code !== App.CODE.SUCCESS) {
						setStatus(res.message || '삭제하지 못했습니다.', true);
						return;
					}
					settings.dirty = false;
					window.location.href = url.list;
				})
				.catch(function(){
					setStatus('삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.', true);
				});
		},

		setStatus = function(text, isError){
			m$.status.textContent = text;
			m$.status.classList.toggle('is-error', !!isError);
		},

		/* 한국 시간 yyyy-mm-dd (input type=date 값) */
		today = function(){
			return new Intl.DateTimeFormat('sv-SE', {timeZone: 'Asia/Seoul'}).format(new Date());
		},

		time = function(){
			return new Intl.DateTimeFormat('ko-KR', {hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Seoul'}).format(new Date());
		};

	return {
		init: init
	};
}());

document.addEventListener('DOMContentLoaded', function(){
	App.postEdit.init();
});
