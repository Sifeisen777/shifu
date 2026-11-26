import http from './http'

/** 商户分类 */
export const fetchCategories = () => http.get('/merchant-category/list')

/** 商户 */
export const fetchMerchant = (id) => http.get(`/merchant/${id}`)
export const fetchMerchantsByType = (params) => http.get('/merchant/of/type', { params })
export const searchMerchants = (params) => http.get('/merchant/of/name', { params })

/** 笔记 */
export const fetchHotNotes = (current = 1) => http.get('/note/hot', { params: { current } })
export const fetchNote = (id) => http.get(`/note/${id}`)
export const fetchMyNotes = (current = 1) => http.get('/note/of/me', { params: { current } })
export const fetchNotesOfUser = (id, current = 1) =>
  http.get('/note/of/user', { params: { id, current } })
export const fetchFollowFeed = (lastId, offset = 0) =>
  http.get('/note/of/follow', { params: { lastId, offset } })
export const fetchNoteLikes = (id) => http.get(`/note/likes/${id}`)
export const toggleLike = (id) => http.put(`/note/like/${id}`)
export const publishNote = (payload) => http.post('/note', payload)

/** 权益卡（券） */
export const fetchBenefits = (merchantId) => http.get(`/benefit/list/${merchantId}`)
export const seckill = (benefitId) => http.post(`/benefit-order/seckill/${benefitId}`)

/** 媒体上传：返回 "/notes/1/2/xxx.jpg"，前端需拼 /imgs 前缀 */
export const uploadImage = (file) => {
  const form = new FormData()
  form.append('file', file)
  return http.post('/media/note', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
export const deleteImage = (name) => http.get('/media/note/delete', { params: { name } })

/** 用户 */
export const sendCode = (phone) => http.post('/user/code', null, { params: { phone } })
export const login = (payload) => http.post('/user/login', payload)
export const logout = () => http.post('/user/logout')
export const fetchMe = () => http.get('/user/me')
export const fetchUser = (id) => http.get(`/user/${id}`)
export const fetchUserProfile = (id) => http.get(`/user/info/${id}`)
export const updateProfile = (payload) => http.put('/user/info', payload)
export const signIn = () => http.post('/user/sign')
export const fetchSignCount = () => http.get('/user/sign/count')

/** 关注 */
export const isFollowing = (id) => http.get(`/follow/or/not/${id}`)
export const follow = (id, value) => http.put(`/follow/${id}/${value}`)
export const fetchCommonFollows = (id) => http.get(`/follow/common/${id}`)
