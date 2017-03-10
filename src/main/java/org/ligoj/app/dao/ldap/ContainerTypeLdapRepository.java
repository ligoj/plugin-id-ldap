package org.ligoj.app.dao.ldap;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.ligoj.bootstrap.core.dao.RestRepository;
import org.ligoj.app.model.ldap.ContainerType;
import org.ligoj.app.model.ldap.ContainerTypeLdap;

/**
 * {@link ContainerTypeLdap} repository.
 */
public interface ContainerTypeLdapRepository extends RestRepository<ContainerTypeLdap, Integer> {

	/**
	 * Return all types, ordered by DN.
	 * 
	 * @param type
	 *            The {@link ContainerType} to filter. Required.
	 * @return types of group.
	 */
	@Query("FROM ContainerTypeLdap WHERE type = ?1 ORDER BY LENGTH(dn) DESC")
	List<ContainerTypeLdap> findAllOrderByDnDesc(ContainerType type);

	/**
	 * Delete an unlocked type.
	 * 
	 * @param id
	 *            identifier of entity to delete.
	 */
	@Modifying
	@Query("DELETE ContainerTypeLdap WHERE id = ?1 AND locked = false")
	void delete(int id);

	/**
	 * Return all types with a criteria by {@link ContainerType}.
	 * 
	 * @param type
	 *            The {@link ContainerType} to filter. Required.
	 * @param criteria
	 *            DN or Name to match.
	 * @param page
	 *            The {@link Pageable} context.
	 * @return types of group.
	 */
	@Query("SELECT g FROM ContainerTypeLdap g WHERE type=:type AND (UPPER(g.name) LIKE UPPER(CONCAT(CONCAT('%',:criteria),'%'))"
			+ " OR UPPER(g.dn) LIKE UPPER(CONCAT(CONCAT('%',:criteria),'%')))")
	Page<ContainerTypeLdap> findAll(@Param("type") ContainerType type, @Param("criteria") String criteria, Pageable page);

	/**
	 * Return all types by {@link ContainerType}.
	 * 
	 * @param type
	 *            The {@link ContainerType} to filter. Required.
	 * @param page
	 *            The {@link Pageable} context.
	 * @return types of group.
	 */
	@Query("SELECT g FROM ContainerTypeLdap g WHERE type=:type")
	Page<ContainerTypeLdap> findAllByType(@Param("type") ContainerType type, Pageable page);
}
