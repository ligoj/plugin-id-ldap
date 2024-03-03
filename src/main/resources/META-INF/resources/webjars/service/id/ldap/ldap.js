/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
define(function () {
	let current = {

		configureSubscriptionParameters: function (configuration, $container) {
			current.registerIdParentGroupSelect2(configuration, $container, 'service:id:parent-group');
			current.registerIdGroupSelect2(configuration, $container, 'service:id:group');
			current.registerIdOuSelect2(configuration, $container, 'service:id:ou');
			if (current.$super('isNodeMode')($container)){
                current.$super('layoutParameters')(configuration, [
                    {'section': 'server'},
                    'service:id:ldap:url',
                    'service:id:ldap:user-dn',
                    'service:id:ldap:password',
                    'service:id:ldap:base-dn',

                    {'section': 'authentication'},
                    'service:id:ldap:clear-password',
                    'service:id:ldap:local-id-attribute',
                    'service:id:ldap:locked-attribute',
                    'service:id:ldap:referral',
                    'service:id:ldap:locked-value',
                    'service:id:uid-pattern',
                    'service:id:ldap:self-search',
                    'service:id:ldap:department-attribute',
                    'service:id:ldap:login-attributes',
                    'service:id:ldap:uid-attribute',

                    {'section': 'people'},
                    'service:id:ldap:people-dn',
                    'service:id:ldap:people-internal-dn',
                    'service:id:ldap:quarantine-dn',
                    'service:id:ldap:people-class',
                    'service:id:ldap:people-class-create',
                    'service:id:ldap:people-custom-attributes',

                    {'section': 'groups'},
                    'service:id:ldap:groups-dn',
                    'service:id:ldap:groups-class',
                    'service:id:ldap:groups-class-create',
                    'service:id:ldap:groups-member-attribute',

                    {'section': 'companies'},
                    'service:id:ldap:companies-dn',
                    'service:id:ldap:company-pattern',
                    'service:id:ldap:companies-class',
                    'service:id:ldap:companies-class-create',
                 ]);
			} else {
               current.$super('layoutParameters')(configuration, ['service:id:ou', 'service:id:parent-group','service:id:group']);
            }
		},

		/**
		 * Render LDAP.
		 */
		renderFeatures: function (subscription) {
			let result = '';
			const now = moment();

			// Add activity export
			result += '<div class="btn-group btn-link" data-container="body" data-toggle="tooltip" title="' + current.$messages['export'] + '">';
			result += ' <i class="fas fa-download" data-toggle="dropdown"></i>';
			result += ' <ul class="dropdown-menu dropdown-menu-right"><li>';
			result += current.$super('renderServiceLink')('file-excel-o', REST_PATH + 'service/id/ldap/activity/' + subscription.id + '/group-' + subscription.parameters['service:id:group'] + '-' + now.format('YYYY-MM-DD') + '.csv', null, 'service:id:activity-group', ' download');
			result += current.$super('renderServiceLink')('file-excel-o', REST_PATH + 'service/id/ldap/activity/' + subscription.id + '/project-' + subscription.parameters['service:id:group'] + '-' + now.format('YYYY-MM-DD') + '.csv', null, 'service:id:activity-project', ' download');
			result += '</li></ul></div>';
			return result;
		},

		/**
		 * Replace the default text rendering by a Select2 for Customers/OU.
		 */
		registerIdOuSelect2: function (configuration, $container, id) {
			if (!current.$super('isNodeMode')($container)) {
				configuration.validators[id] = current.validateIdOuCreateMode;
			}
			current.$super('registerXServiceSelect2')(configuration, id, 'service/id/ldap/customer/', null, true);
		},
		/**
		 * Replace the default text rendering by a Select2 for ParentGroup.
		 */
		registerIdParentGroupSelect2: function (configuration, $container, id) {
			if (!current.$super('isNodeMode')($container)) {
				configuration.validators[id] = current.validateIdGroupCreateMode;
			}
			current.$super('registerXServiceSelect2')(configuration, id, 'service/id/group', '?search[value]=');
		},

		/**
		 * Replace the input by a select2 in link mode. In creation mode, disable manual edition of 'group',
		 * and add a simple text with live
		 * validation regarding existing group and syntax.
		 */
		registerIdGroupSelect2: function (configuration, $container, id) {
			const cProviders = configuration.providers['form-group'];
			const previousProvider = cProviders[id] || cProviders.standard;
			if (configuration.mode === 'create' && !current.$super('isNodeMode')($container)) {
				cProviders[id] = function (parameter, container, $input) {
					// Register a live validation of group
					const simpleGroupId = 'service:id:group-simple-name';
					configuration.validators[simpleGroupId] = current.validateIdGroupCreateMode;

					// Disable computed parameters and remove the description, since it is overridden
					const parentParameter = $.extend({}, parameter);
					parentParameter.description = null;
					const $fieldset = previousProvider(parentParameter, container, $input).parent();
					$input.attr('readonly', 'readonly').removeAttr('required').attr('disabled', 'disabled');
					$input.attr('placeholder', current.$messages ['service:id:ldap:group-create'])
					$input.closest('.form-group').removeClass('required');

					// Create the input corresponding to the last part of the final group name
					const $simpleInput = $('<input class="form-control" type="text" id="' + simpleGroupId + '" required autocomplete="off">');
					cProviders.standard({
						id: simpleGroupId,
						mandatory: true,
						layout: 'prepend',
					}, $fieldset, $simpleInput);
				};
			} else {
				current.$super('registerXServiceSelect2')(configuration, id, 'service/id/ldap/group/');
			}
		},

		/**
		 * Live validation of LDAP OU.
		 */
		validateIdOuCreateMode: function () {
			const $input = _('service:id:ou');
			validationManager.reset($input);
			const data = $input.select2('data');
			if (data?.['new']) {
				// Organization will be created
				validationManager.addWarn($input, {
					rule: 'service:id:ou-not-exists'
				}, 'service:id:ou', true);
			} else if (data) {
				// Existing organization
				validationManager.addSuccess($input, [], null, true);
			}

			// Propagate the validation
			return current.validateIdGroupCreateMode();
		},

		/**
		 * Live validation of LDAP group, OU and parent.
		 */
		validateIdGroupCreateMode: function () {
			validationManager.reset(_('service:id:group'));
			const $input = _('service:id:group');
			const simpleName = _('service:id:group-simple-name').val();
			const prefix = _('service:id:parent-group').val() || _('service:id:ou').val();
			const fullName = (prefix ? prefix + '-' : '') + (simpleName || '').toLowerCase();
			$input.val(fullName).closest('.form-group').find('.form-control-feedback').remove().end().addClass('has-feedback');
			if (fullName !== current.$super('model').pkey && !fullName.startsWith(current.$super('model').pkey + '-')) {
				validationManager.addError($input, {
					rule: 'StartsWith',
					parameters: current.$super('model').pkey
				}, 'group', true);
				return false;
			}
			// Live validation to check the group does not exists
			validationManager.addMessage($input, null, [], null, 'fas fa-sync-alt fa-spin');
			$.ajax({
				dataType: 'json',
				url: REST_PATH + 'service/id/group/' + encodeURIComponent(fullName) + '/exists',
				type: 'GET',
				success: function (data) {
					if (data) {
						// Existing project
						validationManager.addError(_('service:id:group'), {
							rule: 'already-exist',
							parameters: ['service:id:group', fullName]
						}, 'group', true);
					} else {
						// Succeed, not existing project
						validationManager.addSuccess($input, [], null, true);
					}
				}
			});

			// For now return true for the immediate validation system, even if the Ajax call may fail
			return true;
		}
	};
	return current;
});
