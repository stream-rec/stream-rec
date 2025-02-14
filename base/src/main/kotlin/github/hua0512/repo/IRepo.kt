package github.hua0512.repo

import github.hua0512.dao.BaseDao

interface IRepo<T : BaseDao<U>, U : Any> {


  val dao: T

}